FROM mcr.microsoft.com/dotnet/sdk:6.0 AS build

WORKDIR /app

# Copy project file and restore dependencies
COPY *.csproj ./
RUN dotnet restore

# Copy source code and build
COPY . ./
RUN dotnet publish -c Release -o out

# Runtime stage
FROM mcr.microsoft.com/dotnet/runtime:6.0

# install prerequisites
RUN apt update
RUN apt install -y ca-certificates

# add certificates and application
ADD chain_and_key.pem .
ADD ca.pem .
COPY --from=build /app/out .

# execute application
CMD ["dotnet", "SubscriptionExample.dll"]