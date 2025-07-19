using System;
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
using Amqp.Listener;
using Amqp.Sasl;
using Amqp.Types;

class Program
{
    // Configuration by environment variables
    private static readonly string ACTOR_API_HOST = Environment.GetEnvironmentVariable("ACTOR_API_HOST") ?? "hostname_of_the_actor_api";
    private static readonly string ACTOR_API_PORT = Environment.GetEnvironmentVariable("ACTOR_API_PORT") ?? "port_of_the_actor_api";
    private static readonly string ACTOR_API_SUBSCRIPTION_SELECTOR = Environment.GetEnvironmentVariable("ACTOR_API_SUBSCRIPTION_SELECTOR") ?? "selector_of_the_subscription";
    private static readonly string ACTOR_COMMON_NAME = Environment.GetEnvironmentVariable("ACTOR_COMMON_NAME") ?? "cn_of_the_actor_client_certificate";
    private static readonly string ACTOR_CERTIFICATE_CHAIN_AND_KEY_PEM = Environment.GetEnvironmentVariable("ACTOR_CERTIFICATE_CHAIN_AND_KEY_PEM") ?? "pem_with_x509_certificate_chain_and_private_key";
    private static readonly string CA_CERTIFICATE_PEM = Environment.GetEnvironmentVariable("CA_CERTIFICATE_PEM") ?? "pem_with_x509_certificate";

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

    private static async Task<HttpResponseMessage> ApiGetSubscriptionAsync(string id, HttpClient client)
    {
        return await ApiGetAsync($"subscriptions/{id}", client);
    }

    private static async Task<HttpResponseMessage> ApiDeleteSubscriptionAsync(string id, HttpClient client)
    {
        return await ApiDeleteAsync($"subscriptions/{id}", client);
    }

    private static async Task<HttpResponseMessage> ApiCreateSubscriptionAsync(HttpClient client)
    {
        var jsonData = new { selector = ACTOR_API_SUBSCRIPTION_SELECTOR };
        return await ApiPostAsync("subscriptions", jsonData, client);
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

    private static void PrintMessageDetails(Message message)
    {
        // Decode binary body as UTF-8
        var bodyText = "";
        if (message.Body is byte[] bodyBytes)
        {
            bodyText = Encoding.UTF8.GetString(bodyBytes);
        }
        else
        {
            bodyText = message.Body?.ToString() ?? "";
        }
        
        // Format application properties as JSON in sorted order
        var appPropsJson = "{}";
        if (message.ApplicationProperties != null && message.ApplicationProperties.Map != null)
        {
            var propsDict = new Dictionary<string, object>();
            foreach (var kvp in message.ApplicationProperties.Map)
            {
                propsDict[kvp.Key.ToString()] = kvp.Value;
            }
            // Sort keys for consistent output
            var sortedProps = propsDict.OrderBy(x => x.Key).ToDictionary(x => x.Key, x => x.Value);
            appPropsJson = JsonSerializer.Serialize(sortedProps, new JsonSerializerOptions { WriteIndented = false });
        }
        
        LogInfo($"Message received: body='{bodyText}', properties={appPropsJson}");
    }

    private static async Task AmqpConnectAndListenAsync(SubscriptionEndpoint endpoint)
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
        var receiver = new ReceiverLink(session, "receiver-link", endpoint.Source);

        LogInfo("Listening for messages. Press Ctrl+C to stop.");
        
        var cts = new CancellationTokenSource();
        Console.CancelKeyPress += (sender, e) => {
            e.Cancel = true;
            cts.Cancel();
        };

        try
        {
            while (!cts.Token.IsCancellationRequested)
            {
                var message = await receiver.ReceiveAsync(TimeSpan.FromSeconds(1));
                if (message != null)
                {
                    PrintMessageDetails(message);
                    receiver.Accept(message);
                }
            }
        }
        catch (OperationCanceledException)
        {
            LogInfo("Stopping message listener...");
        }

        await receiver.CloseAsync();
        await session.CloseAsync();
        await connection.CloseAsync();
    }

    // ======== CREATE AND CONSUME A SUBSCRIPTION ========
    private static void LogJson(string message, object jsonObj)
    {
        var jsonString = JsonSerializer.Serialize(jsonObj, new JsonSerializerOptions { WriteIndented = true });
        LogInfo($"{message}: {jsonString}");
    }

    private static async Task SubscribeAndReceiveAsync()
    {
        using var client = CreateHttpClient();
        
        try
        {
            // Step 1: create a subscription using the actor API
            var subscriptionCreateResponse = await ApiCreateSubscriptionAsync(client);
            var subscriptionCreateResponseContent = await subscriptionCreateResponse.Content.ReadAsStringAsync();
            LogDebug($"Raw subscription create response: {subscriptionCreateResponseContent}");
            LogDebug($"HTTP Status Code: {subscriptionCreateResponse.StatusCode}");
            
            var subscriptionCreateResponseJson = JsonSerializer.Deserialize<SubscriptionCreateResponse>(subscriptionCreateResponseContent);
            LogJson("Subscription create response", subscriptionCreateResponseJson);

            if (subscriptionCreateResponse.IsSuccessStatusCode)
            {
                // Step 2: get the subscription status
                var subscriptionId = subscriptionCreateResponseJson.Id;
                LogDebug($"Subscription ID: '{subscriptionId}'");
                
                if (string.IsNullOrEmpty(subscriptionId))
                {
                    LogError("Error: Subscription ID is empty or null");
                    return;
                }
                
                var subscriptionStatusResponse = await ApiGetSubscriptionAsync(subscriptionId, client);
                var subscriptionStatusResponseContent = await subscriptionStatusResponse.Content.ReadAsStringAsync();
                LogDebug($"Raw subscription status response: {subscriptionStatusResponseContent}");
                
                var subscriptionStatusResponseJson = JsonSerializer.Deserialize<SubscriptionStatusResponse>(subscriptionStatusResponseContent);
                LogJson($"Subscription {subscriptionId} status response", subscriptionStatusResponseJson);
                var subscriptionStatus = subscriptionStatusResponseJson.Status;

                // Step 3: while the subscription status is "REQUESTED", keep getting the status
                while (subscriptionStatus == "REQUESTED")
                {
                    await Task.Delay(2000);
                    subscriptionStatusResponse = await ApiGetSubscriptionAsync(subscriptionId, client);
                    subscriptionStatusResponseContent = await subscriptionStatusResponse.Content.ReadAsStringAsync();
                    subscriptionStatusResponseJson = JsonSerializer.Deserialize<SubscriptionStatusResponse>(subscriptionStatusResponseContent);
                    subscriptionStatus = subscriptionStatusResponseJson.Status;
                }

                LogJson($"Subscription {subscriptionId} status response", subscriptionStatusResponseJson);
                
                // Step 4a: if the status is "CREATED", connect to the endpoint and start the AMQP receiver
                if (subscriptionStatus == "CREATED")
                {
                    // NOTE to keep things simple, this code assumes that this response contains exactly one endpoint!
                    var endpoint = subscriptionStatusResponseJson.Endpoints[0];
                    LogInfo($"Using endpoint {JsonSerializer.Serialize(endpoint)}");
                    await AmqpConnectAndListenAsync(endpoint);
                }
                // Step 4b: if the status is not "CREATED" warn log and do nothing
                else
                {
                    LogError($"Unable to use subscription {subscriptionId}");
                }
            }
        }
        catch (Exception e)
        {
            LogError($"An exception occurred while running SubscribeAndReceive: {e.Message}");
        }
    }

    // ======== STARTUP AND RUN LOOP ========
    private static void DumpConfig()
    {
        LogDebug($"ACTOR_API_HOST: '{ACTOR_API_HOST}'");
        LogDebug($"ACTOR_API_PORT: '{ACTOR_API_PORT}'");
        LogDebug($"ACTOR_API_SUBSCRIPTION_SELECTOR: '{ACTOR_API_SUBSCRIPTION_SELECTOR}'");
        LogDebug($"ACTOR_COMMON_NAME: '{ACTOR_COMMON_NAME}'");
        LogDebug($"ACTOR_CERTIFICATE_CHAIN_AND_KEY_PEM: '{ACTOR_CERTIFICATE_CHAIN_AND_KEY_PEM}'");
        LogDebug($"CA_CERTIFICATE_PEM: '{CA_CERTIFICATE_PEM}'");
    }

    static async Task Main(string[] args)
    {
        LogInfo("Starting application");
        DumpConfig();
        
        try
        {
            await SubscribeAndReceiveAsync();
        }
        catch (Exception e)
        {
            LogError($"Application error: {e.Message}");
        }
        
        LogInfo("Application stopped");
    }
}

// ======== DATA CLASSES ========
public class SubscriptionCreateResponse
{
    [JsonPropertyName("id")]
    public string Id { get; set; } = "";
}

public class SubscriptionStatusResponse
{
    [JsonPropertyName("status")]
    public string Status { get; set; } = "";
    [JsonPropertyName("endpoints")]
    public SubscriptionEndpoint[] Endpoints { get; set; } = new SubscriptionEndpoint[0];
}

public class SubscriptionEndpoint
{
    [JsonPropertyName("host")]
    public string Host { get; set; } = "";
    [JsonPropertyName("port")]
    public int Port { get; set; }
    [JsonPropertyName("source")]
    public string Source { get; set; } = "";
}