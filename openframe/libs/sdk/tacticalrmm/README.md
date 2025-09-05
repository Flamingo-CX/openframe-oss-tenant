# Tactical RMM SDK

Independent Java SDK for Tactical RMM REST API integration.

## Features

- Get installation secret from agents installer endpoint
- Lightweight and dependency-free (only Jackson for JSON processing)
- Comprehensive unit tests
- Java 17+ compatible

## Installation

### Maven

Add the SDK to your project dependencies:

```xml
<dependency>
    <groupId>com.openframe</groupId>
    <artifactId>tacticalrmm</artifactId>
    <version>1.0.0</version>
  </dependency>
```

### Manual Installation

1. Clone this repository
2. Build the SDK: `mvn clean install`
3. Add the generated JAR to your project

## Usage

### Basic Example

```java
import com.openframe.sdk.tacticalrmm.TacticalRmmClient;

// Initialize client
TacticalRmmClient client = new TacticalRmmClient("http://tactical-nginx.integrated-tools.svc.cluster.local:8000");

// Get installation secret
String secret = client.getInstallationSecret();
System.out.println("Installer secret: " + secret);
```

## Development

### Building

```bash
mvn clean install
```

### Running Tests

```bash
mvn test
```

## API Reference

### TacticalRmmClient

Main client class for Tactical RMM API integration.

#### Constructor

```java
TacticalRmmClient(String baseUrl)
```

- `baseUrl` - Base URL of Tactical RMM (e.g., `http://tactical-nginx.integrated-tools.svc.cluster.local:8000`)

#### Methods

- `String getInstallationSecret()` - Returns installer secret (or null on 404)

## Error Handling

The SDK throws `RuntimeException` for API errors (non-200 status codes). For 404 errors when getting the installer secret, the method returns `null`.


