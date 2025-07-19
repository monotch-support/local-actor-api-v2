using System;
using System.Collections.Generic;
using System.IO;
using System.Linq;
using System.Security.Cryptography.X509Certificates;
using System.Text;
using System.Text.Json;
using System.Threading;
using System.Threading.Tasks;
using Amqp;
using Amqp.Framing;
using Amqp.Sasl;
using Amqp.Types;

class Program
{
    // Configuration by environment variables
    private static readonly string ACTOR_CERTIFICATE_CHAIN_AND_KEY_PEM = Environment.GetEnvironmentVariable("ACTOR_CERTIFICATE_CHAIN_AND_KEY_PEM") ?? "pem_with_x509_certificate_chain_and_private_key";
    private static readonly string CA_CERTIFICATE_PEM = Environment.GetEnvironmentVariable("CA_CERTIFICATE_PEM") ?? "pem_with_x509_certificate";
    private static readonly string MESSAGE_APPLICATION_PROPERTIES_JSON = Environment.GetEnvironmentVariable("MESSAGE_APPLICATION_PROPERTIES_JSON") ?? "message_application_properties_json";

    // Pre-known endpoint information
    private static readonly string ENDPOINT_HOST = Environment.GetEnvironmentVariable("ENDPOINT_HOST") ?? "amqp_endpoint_host";
    private static readonly string ENDPOINT_PORT = Environment.GetEnvironmentVariable("ENDPOINT_PORT") ?? "amqp_endpoint_port";
    private static readonly string ENDPOINT_TARGET = Environment.GetEnvironmentVariable("ENDPOINT_TARGET") ?? "amqp_endpoint_target_address";

    // ======== LOGGING ========
    private static void LogInfo(string message)
    {
        Console.WriteLine($"{DateTime.Now:yyyy-MM-dd HH:mm:ss,fff} INFO {message}");
    }
    
    private static void LogError(string message)
    {
        Console.WriteLine($"{DateTime.Now:yyyy-MM-dd HH:mm:ss,fff} ERROR {message}");
    }
    
    private static void LogDebug(string message)
    {
        Console.WriteLine($"{DateTime.Now:yyyy-MM-dd HH:mm:ss,fff} DEBUG {message}");
    }

    // ======== AMQP 1.0 CLIENT ========
    private static ConnectionFactory CreateConnectionFactory()
    {
        var factory = new ConnectionFactory();
        
        try
        {
            // Configure SSL/TLS with client certificate for SASL EXTERNAL
            // Read the combined PEM file content
            var certAndKeyPem = File.ReadAllText(ACTOR_CERTIFICATE_CHAIN_AND_KEY_PEM);
            var clientCert = X509Certificate2.CreateFromPem(certAndKeyPem, certAndKeyPem);
            factory.SSL.ClientCertificates.Add(clientCert);
            
            // Enable SSL/TLS
            factory.SSL.Protocols = System.Security.Authentication.SslProtocols.Tls13;
            
            // Load CA certificate for validation
            var caCertPem = File.ReadAllText(CA_CERTIFICATE_PEM);
            var caCert = X509Certificate2.CreateFromPem(caCertPem);
            factory.SSL.RemoteCertificateValidationCallback = (sender, cert, chain, errors) => {
                // Validate against CA certificate
                return ValidateCertificate(cert as X509Certificate2, caCert);
            };
            
            // Extract common name for SASL EXTERNAL
            var commonName = ExtractCommonName(clientCert.Subject);
            LogDebug($"Certificate Subject: {clientCert.Subject}");
            LogDebug($"Extracted Common Name: {commonName}");
            
            // Configure custom SASL EXTERNAL profile that sends only the CN value
            factory.SASL.Profile = new CustomSaslExternalProfile(commonName);
        }
        catch (Exception ex)
        {
            LogError($"Error loading certificates: {ex.Message}");
            throw;
        }
        
        return factory;
    }
    
    private static bool ValidateCertificate(X509Certificate2? serverCert, X509Certificate2 caCert)
    {
        if (serverCert == null || caCert == null)
            return false;
            
        var chain = new X509Chain();
        chain.ChainPolicy.ExtraStore.Add(caCert);
        chain.ChainPolicy.RevocationMode = X509RevocationMode.NoCheck;
        chain.ChainPolicy.VerificationFlags = X509VerificationFlags.AllowUnknownCertificateAuthority;
        
        return chain.Build(serverCert);
    }
    
    private static string ExtractCommonName(string subjectDn)
    {
        // Extract CN value from Distinguished Name (e.g., "CN=XX99999, O=Company" -> "XX99999")
        var parts = subjectDn.Split(',');
        foreach (var part in parts)
        {
            var trimmed = part.Trim();
            if (trimmed.StartsWith("CN="))
            {
                return trimmed.Substring(3);
            }
        }
        return subjectDn; // Fallback to full DN if CN not found
    }
    
    // Custom SASL EXTERNAL profile that sends only the CN value
    public class CustomSaslExternalProfile : SaslProfile
    {
        private readonly string identity;

        public CustomSaslExternalProfile(string identity) : base(new Symbol("EXTERNAL"))
        {
            this.identity = identity;
        }

        protected override DescribedList GetStartCommand(string hostname)
        {
            return new SaslInit()
            {
                Mechanism = "EXTERNAL",
                InitialResponse = Encoding.UTF8.GetBytes(identity)
            };
        }

        protected override DescribedList OnCommand(DescribedList command)
        {
            // For EXTERNAL, we should only need the initial response
            return null!;
        }

        protected override ITransport UpgradeTransport(ITransport transport)
        {
            // No transport upgrade needed for EXTERNAL
            return transport;
        }
    }

    private static async Task AmqpConnectAndPublishAsync(DeliveryEndpoint endpoint)
    {
        var factory = CreateConnectionFactory();
        
        // Extract CN from certificate for user identity
        var certAndKeyPem = File.ReadAllText(ACTOR_CERTIFICATE_CHAIN_AND_KEY_PEM);
        var clientCert = X509Certificate2.CreateFromPem(certAndKeyPem, certAndKeyPem);
        var commonName = ExtractCommonName(clientCert.Subject);
        
        // Create connection with proper user identity
        var address = new Address($"amqps://{endpoint.Host}:{endpoint.Port}");
        var connection = await factory.CreateAsync(address);
        
        var session = new Session(connection);
        var sender = new SenderLink(session, "sender-link", endpoint.Target);

        var messageProperties = JsonSerializer.Deserialize<Dictionary<string, JsonElement>>(MESSAGE_APPLICATION_PROPERTIES_JSON);
        
        LogDebug("Container reactor started");
        
        int messageCount = 0;
        
        // Send messages continuously
        while (true)
        {
            // Increment message counter
            messageCount++;
            // Create dynamic message content with counter and timestamp
            var bodyText = $"Hello World! Message #{messageCount} at {DateTime.Now:HH:mm:ss}";
            var bodyBinary = Encoding.UTF8.GetBytes(bodyText);
            var message = new Message()
            {
                BodySection = new Data() { Binary = bodyBinary },
                ApplicationProperties = new ApplicationProperties()
            };

            foreach (var prop in messageProperties)
            {
                // Convert JsonElement to proper .NET types for AMQP
                object value = prop.Value.ValueKind switch
                {
                    JsonValueKind.String => prop.Value.GetString(),
                    JsonValueKind.Number => prop.Value.GetInt32(),
                    JsonValueKind.True => true,
                    JsonValueKind.False => false,
                    JsonValueKind.Null => null,
                    _ => prop.Value.ToString()
                };
                message.ApplicationProperties[prop.Key] = value;
            }
            
            // Format properties in sorted order for consistent logging
            var messagePropsDict = JsonSerializer.Deserialize<Dictionary<string, JsonElement>>(MESSAGE_APPLICATION_PROPERTIES_JSON);
            var sortedPropsJson = JsonSerializer.Serialize(messagePropsDict, new JsonSerializerOptions { WriteIndented = false });
            LogInfo($"Sending message: body='{bodyText}', properties={sortedPropsJson}");
            await sender.SendAsync(message);
            
            // Wait before sending the next message
            await Task.Delay(1000);
        }

        await sender.CloseAsync();
        await session.CloseAsync();
        await connection.CloseAsync();
    }

    // ======== DIRECT PUBLISH WITH KNOWN ENDPOINT ========
    private static async Task DirectPublishAsync()
    {
        try
        {
            // Create endpoint from environment variables
            var endpoint = new DeliveryEndpoint
            {
                Host = ENDPOINT_HOST,
                Port = int.Parse(ENDPOINT_PORT),
                Target = ENDPOINT_TARGET
            };
            
            LogInfo($"Using pre-known endpoint {JsonSerializer.Serialize(endpoint)}");
            await AmqpConnectAndPublishAsync(endpoint);
        }
        catch (Exception e)
        {
            LogError($"An exception occurred while running DirectPublish: {e.Message}");
        }
    }

    // ======== STARTUP AND RUN LOOP ========
    private static void DumpConfig()
    {
        LogDebug($"ACTOR_CERTIFICATE_CHAIN_AND_KEY_PEM: '{ACTOR_CERTIFICATE_CHAIN_AND_KEY_PEM}'");
        LogDebug($"CA_CERTIFICATE_PEM: '{CA_CERTIFICATE_PEM}'");
        LogDebug($"MESSAGE_APPLICATION_PROPERTIES_JSON: '{MESSAGE_APPLICATION_PROPERTIES_JSON}'");
        LogDebug($"ENDPOINT_HOST: '{ENDPOINT_HOST}'");
        LogDebug($"ENDPOINT_PORT: '{ENDPOINT_PORT}'");
        LogDebug($"ENDPOINT_TARGET: '{ENDPOINT_TARGET}'");
    }

    static async Task Main(string[] args)
    {
        LogInfo("Starting application");
        DumpConfig();
        
        try
        {
            await DirectPublishAsync();
        }
        catch (Exception e)
        {
            LogError($"Application error: {e.Message}");
        }
        
        LogInfo("Application stopped");
    }
}

// ======== DATA CLASSES ========
public class DeliveryEndpoint
{
    public string Host { get; set; } = "";
    public int Port { get; set; }
    public string Target { get; set; } = "";
}