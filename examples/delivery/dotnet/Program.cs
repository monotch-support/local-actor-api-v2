using System;
using System.Collections.Generic;
using System.IO;
using System.Linq;
using System.Net.Http;
using System.Security.Cryptography.X509Certificates;
using System.Text;
using System.Text.Json;
using System.Text.Json.Serialization;
using System.Threading;
using System.Threading.Tasks;
using Amqp;
using Amqp.Framing;
using Amqp.Sasl;
using Amqp.Types;

class Program
{
    // Configuration by environment variables
    private static readonly string ACTOR_API_HOST = Environment.GetEnvironmentVariable("ACTOR_API_HOST") ?? "hostname_of_the_actor_api";
    private static readonly string ACTOR_API_PORT = Environment.GetEnvironmentVariable("ACTOR_API_PORT") ?? "port_of_the_actor_api";
    private static readonly string ACTOR_API_DELIVERY_SELECTOR = Environment.GetEnvironmentVariable("ACTOR_API_DELIVERY_SELECTOR") ?? "selector_of_the_delivery";
    private static readonly string ACTOR_COMMON_NAME = Environment.GetEnvironmentVariable("ACTOR_COMMON_NAME") ?? "cn_of_the_actor_client_certificate";
    private static readonly string ACTOR_CERTIFICATE_CHAIN_AND_KEY_PEM = Environment.GetEnvironmentVariable("ACTOR_CERTIFICATE_CHAIN_AND_KEY_PEM") ?? "pem_with_x509_certificate_chain_and_private_key";
    private static readonly string CA_CERTIFICATE_PEM = Environment.GetEnvironmentVariable("CA_CERTIFICATE_PEM") ?? "pem_with_x509_certificate";
    private static readonly string MESSAGE_APPLICATION_PROPERTIES_JSON = Environment.GetEnvironmentVariable("MESSAGE_APPLICATION_PROPERTIES_JSON") ?? "message_application_properties_json";

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

    // ======== ACTOR API FUNCTIONS ========
    private static HttpClient CreateHttpClient()
    {
        var handler = new HttpClientHandler();
        
        try
        {
            // Load client certificate using the same method as AMQP
            var certAndKeyPem = File.ReadAllText(ACTOR_CERTIFICATE_CHAIN_AND_KEY_PEM);
            var clientCert = X509Certificate2.CreateFromPem(certAndKeyPem, certAndKeyPem);
            handler.ClientCertificates.Add(clientCert);
            
            // Load CA certificate for server validation
            var caCertPem = File.ReadAllText(CA_CERTIFICATE_PEM);
            var caCert = X509Certificate2.CreateFromPem(caCertPem);
            handler.ServerCertificateCustomValidationCallback = (sender, cert, chain, errors) => {
                return chain.ChainElements[chain.ChainElements.Count - 1].Certificate.Equals(caCert);
            };
        }
        catch (Exception ex)
        {
            LogError($"Error loading certificates for HTTP client: {ex.Message}");
            throw;
        }

        return new HttpClient(handler);
    }
    
    private static string ApiUrl(string endpoint)
    {
        return $"https://{ACTOR_API_HOST}:{ACTOR_API_PORT}/{ACTOR_COMMON_NAME}/{endpoint}";
    }

    private static async Task<HttpResponseMessage> ApiGetAsync(string endpoint, HttpClient client)
    {
        return await client.GetAsync(ApiUrl(endpoint));
    }

    private static async Task<HttpResponseMessage> ApiPostAsync(string endpoint, object jsonData, HttpClient client)
    {
        var json = JsonSerializer.Serialize(jsonData);
        var content = new StringContent(json, Encoding.UTF8, "application/json");
        return await client.PostAsync(ApiUrl(endpoint), content);
    }

    private static async Task<HttpResponseMessage> ApiDeleteAsync(string endpoint, HttpClient client)
    {
        return await client.DeleteAsync(ApiUrl(endpoint));
    }

    private static async Task<HttpResponseMessage> ApiGetDeliveryAsync(string id, HttpClient client)
    {
        return await ApiGetAsync($"deliveries/{id}", client);
    }

    private static async Task<HttpResponseMessage> ApiDeleteDeliveryAsync(string id, HttpClient client)
    {
        return await ApiDeleteAsync($"deliveries/{id}", client);
    }

    private static async Task<HttpResponseMessage> ApiCreateDeliveryAsync(HttpClient client)
    {
        var jsonData = new { selector = ACTOR_API_DELIVERY_SELECTOR };
        return await ApiPostAsync("deliveries", jsonData, client);
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
        
        LogInfo("Starting to send messages continuously. Press Ctrl+C to stop.");
        
        var cts = new CancellationTokenSource();
        Console.CancelKeyPress += (sender_event, e) => {
            e.Cancel = true;
            cts.Cancel();
        };
        
        int messageCount = 0;
        
        try
        {
            while (!cts.Token.IsCancellationRequested)
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

                // Format application properties for logging
                var appPropsString = "";
                if (message.ApplicationProperties != null && message.ApplicationProperties.Map != null)
                {
                    var props = message.ApplicationProperties.Map.Select(kvp => $"{kvp.Key}={kvp.Value}");
                    appPropsString = " [" + string.Join(", ", props) + "]";
                }
                
                LogInfo($"Sending message #{messageCount}: {message.Body}{appPropsString}");
                await sender.SendAsync(message);
                
                // Wait 1 second before sending the next message
                await Task.Delay(1000, cts.Token);
            }
        }
        catch (OperationCanceledException)
        {
            LogInfo($"Stopping message sending... Sent {messageCount} messages total.");
        }

        await sender.CloseAsync();
        await session.CloseAsync();
        await connection.CloseAsync();
    }

    // ======== CREATE AND PUBLISH INTO A DELIVERY ========
    private static void LogJson(string message, object jsonObj)
    {
        var jsonString = JsonSerializer.Serialize(jsonObj, new JsonSerializerOptions { WriteIndented = true });
        LogInfo($"{message}: {jsonString}");
    }

    private static async Task CreateAndPublishAsync()
    {
        using var client = CreateHttpClient();
        
        try
        {
            // Step 1: create a delivery using the actor API
            var deliveryCreateResponse = await ApiCreateDeliveryAsync(client);
            var deliveryCreateResponseContent = await deliveryCreateResponse.Content.ReadAsStringAsync();
            var deliveryCreateResponseJson = JsonSerializer.Deserialize<DeliveryCreateResponse>(deliveryCreateResponseContent);
            LogJson("Delivery create response", deliveryCreateResponseJson);

            if (deliveryCreateResponse.IsSuccessStatusCode)
            {
                // Step 2: get the delivery status
                var deliveryId = deliveryCreateResponseJson.Id;
                var deliveryStatusResponse = await ApiGetDeliveryAsync(deliveryId, client);
                var deliveryStatusResponseContent = await deliveryStatusResponse.Content.ReadAsStringAsync();
                var deliveryStatusResponseJson = JsonSerializer.Deserialize<DeliveryStatusResponse>(deliveryStatusResponseContent);
                LogJson($"Delivery {deliveryId} status response", deliveryStatusResponseJson);
                var deliveryStatus = deliveryStatusResponseJson.Status;

                // Step 3: while the delivery status is "REQUESTED", keep getting the status
                while (deliveryStatus == "REQUESTED")
                {
                    await Task.Delay(2000);
                    deliveryStatusResponse = await ApiGetDeliveryAsync(deliveryId, client);
                    deliveryStatusResponseContent = await deliveryStatusResponse.Content.ReadAsStringAsync();
                    deliveryStatusResponseJson = JsonSerializer.Deserialize<DeliveryStatusResponse>(deliveryStatusResponseContent);
                    deliveryStatus = deliveryStatusResponseJson.Status;
                }

                LogJson($"Delivery {deliveryId} status response", deliveryStatusResponseJson);
                
                // Step 4a: if the status is "CREATED", get the endpoint information from the status response and use the endpoint with the AMQP 1.0 client
                if (deliveryStatus == "CREATED")
                {
                    // NOTE to keep things simple, this code assumes that this response contains exactly one endpoint!
                    var endpoint = deliveryStatusResponseJson.Endpoints[0];
                    LogInfo($"Using endpoint {JsonSerializer.Serialize(endpoint)}");
                    await AmqpConnectAndPublishAsync(endpoint);
                }
                // Step 4b: if the status is not "CREATED" warn log and do nothing
                else
                {
                    LogError($"Unable to use delivery {deliveryId}");
                }
            }
        }
        catch (Exception e)
        {
            LogError($"An exception occurred while running CreateAndPublish: {e.Message}");
        }
    }

    // ======== STARTUP AND RUN LOOP ========
    private static void DumpConfig()
    {
        LogDebug($"ACTOR_API_HOST: '{ACTOR_API_HOST}'");
        LogDebug($"ACTOR_API_PORT: '{ACTOR_API_PORT}'");
        LogDebug($"ACTOR_API_DELIVERY_SELECTOR: '{ACTOR_API_DELIVERY_SELECTOR}'");
        LogDebug($"ACTOR_COMMON_NAME: '{ACTOR_COMMON_NAME}'");
        LogDebug($"ACTOR_CERTIFICATE_CHAIN_AND_KEY_PEM: '{ACTOR_CERTIFICATE_CHAIN_AND_KEY_PEM}'");
        LogDebug($"CA_CERTIFICATE_PEM: '{CA_CERTIFICATE_PEM}'");
        LogDebug($"MESSAGE_APPLICATION_PROPERTIES_JSON: '{MESSAGE_APPLICATION_PROPERTIES_JSON}'");
    }

    static async Task Main(string[] args)
    {
        LogInfo("Starting application");
        DumpConfig();
        
        try
        {
            await CreateAndPublishAsync();
        }
        catch (Exception e)
        {
            LogError($"Application error: {e.Message}");
        }
        
        LogInfo("Application stopped");
    }
}

// ======== DATA CLASSES ========
public class DeliveryCreateResponse
{
    [JsonPropertyName("id")]
    public string Id { get; set; } = "";
}

public class DeliveryStatusResponse
{
    [JsonPropertyName("status")]
    public string Status { get; set; } = "";
    [JsonPropertyName("endpoints")]
    public DeliveryEndpoint[] Endpoints { get; set; } = new DeliveryEndpoint[0];
}

public class DeliveryEndpoint
{
    [JsonPropertyName("host")]
    public string Host { get; set; } = "";
    [JsonPropertyName("port")]
    public int Port { get; set; }
    [JsonPropertyName("target")]
    public string Target { get; set; } = "";
}