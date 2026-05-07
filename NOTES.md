# Restaurant Review App Notes

These notes capture the main things learned while building the Spring Boot restaurant review app. They are organized as a future reference, with short explanations, examples, and project-specific decisions.

## Table of Contents

1. [Configuration](#configuration)
2. [Lombok and MapStruct](#lombok-and-mapstruct)
3. [Domain Modeling](#domain-modeling)
4. [Elasticsearch](#elasticsearch)
5. [Lombok and Entity Classes](#lombok-and-entity-classes)
6. [Docker Workflow](#docker-workflow)
7. [Authentication with Keycloak](#authentication-with-keycloak)
8. [Keycloak Security Risks](#keycloak-security-risks)
9. [Invite-Only Users with Temporary Passwords](#invite-only-users-with-temporary-passwords)
10. [Photo Upload](#photo-upload)
11. [Geolocation](#geolocation)
12. [Java Streams API](#java-streams-api)

## Configuration

### Purpose of `application.properties`

`application.properties` keeps configuration separate from code.

This lets you change things like ports, database URLs, credentials, Elasticsearch settings, or Keycloak issuer URLs without editing Java classes.

Example:

```properties
server.port=8080
spring.elasticsearch.uris=http://localhost:9200
spring.security.oauth2.resourceserver.jwt.issuer-uri=http://localhost:9090/realms/restaurant
```

The main benefit is that different environments can use different configuration:

```text
Local development  -> local Docker services
Staging            -> staging infrastructure
Production         -> production infrastructure and secrets
```

## Lombok and MapStruct

### Mental Model

Lombok and MapStruct both generate code during compilation.

```text
Lombok     -> generates getters, setters, constructors, builders, etc.
MapStruct  -> generates mapper implementation classes
Binding    -> helps MapStruct understand Lombok-generated methods
```

The important detail is that MapStruct needs to see the methods Lombok generates. Without the binding dependency, MapStruct may run before Lombok has made getters/builders visible, which can cause mapper errors.

### Maven Setup Checklist

Define versions once:

```xml
<properties>
    <lombok.version>1.18.36</lombok.version>
    <org.mapstruct.version>1.6.3</org.mapstruct.version>
</properties>
```

Add normal dependencies:

```xml
<dependency>
    <groupId>org.projectlombok</groupId>
    <artifactId>lombok</artifactId>
    <version>${lombok.version}</version>
    <optional>true</optional>
</dependency>

<dependency>
    <groupId>org.mapstruct</groupId>
    <artifactId>mapstruct</artifactId>
    <version>${org.mapstruct.version}</version>
</dependency>
```

Configure annotation processors in the compiler plugin:

```xml
<plugin>
    <groupId>org.apache.maven.plugins</groupId>
    <artifactId>maven-compiler-plugin</artifactId>
    <configuration>
        <annotationProcessorPaths>
            <path>
                <groupId>org.projectlombok</groupId>
                <artifactId>lombok</artifactId>
                <version>${lombok.version}</version>
            </path>
            <path>
                <groupId>org.mapstruct</groupId>
                <artifactId>mapstruct-processor</artifactId>
                <version>${org.mapstruct.version}</version>
            </path>
            <path>
                <groupId>org.projectlombok</groupId>
                <artifactId>lombok-mapstruct-binding</artifactId>
                <version>0.2.0</version>
            </path>
        </annotationProcessorPaths>
    </configuration>
</plugin>
```

Rule to remember:

```text
Regular dependencies go in <dependencies>.
Annotation processors go in maven-compiler-plugin -> annotationProcessorPaths.
Declare the compiler plugin once.
Use lombok-mapstruct-binding when Lombok and MapStruct are used together.
```

## Domain Modeling

### Keycloak and the User Entity

Keycloak is an Identity Provider. It owns authentication-related data and behavior:

```text
Registration       -> Keycloak
Login/logout       -> Keycloak
Passwords          -> Keycloak
JWT tokens         -> Keycloak
Auth roles         -> Keycloak
```

When a user logs in, Keycloak gives the client a JWT. The token contains claims such as:

```json
{
  "sub": "a1b2c3-uuid",
  "preferred_username": "john_doe",
  "given_name": "John",
  "email": "john@example.com",
  "roles": ["user", "admin"]
}
```

### Do We Need a Local `User` Entity?

Not always.

You may not need a local `User` entity if the app only needs to know whether someone is logged in, and all user information can come directly from the JWT.

For this restaurant review app, a local `User` entity is useful because app data needs to link back to a user:

```text
Review     -> belongs to a user
Restaurant -> may be created by a user
Rating     -> submitted by a user
```

Keycloak stores identity data. The application database stores application relationships.

### Thin User Entity Pattern

Do not copy everything from Keycloak into your database. Store only what the app needs.

```java
@Document(indexName = "users")
public class User {

    @Id
    private String id;        // Same as Keycloak "sub"

    @Field(type = FieldType.Keyword)
    private String username;

    @Field(type = FieldType.Text)
    private String givenName;
}
```

The important bridge is:

```text
Keycloak JWT "sub" claim == local User.id
```

### Runtime Flow

```text
User logs in via Keycloak
        |
Keycloak issues JWT
        |
User calls Spring Boot API with the token
        |
Spring Security validates the token
        |
App extracts the "sub" claim
        |
App looks up or creates local User
        |
Reviews, ratings, and restaurants can reference that User
```

### Why `String` for IDs?

Elasticsearch document IDs are strings internally. Keycloak's `sub` claim is also a string UUID.

Using `String` avoids unnecessary conversion between `UUID`, `Long`, and Elasticsearch string IDs.

For entities like `Restaurant` or `Review`, Elasticsearch can generate string IDs automatically if you do not provide one.

### Why Not Store Reviews Inside Restaurant?

Avoid embedding a growing list of reviews directly inside the `Restaurant` document.

Bad idea:

```java
@Field(type = FieldType.Nested)
private List<Review> reviews;
```

A popular restaurant could have thousands of reviews. Loading the restaurant would also load all reviews, even when you only need the restaurant name, address, or rating.

Better approach:

```java
@Field(type = FieldType.Keyword)
private String restaurantId;
```

The `Review` knows which restaurant it belongs to. When reviews are needed, query them separately by `restaurantId`.

## Elasticsearch

### What `@Field` Does

`@Field` comes from Spring Data Elasticsearch. It tells Elasticsearch how to index a field.

This matters because Elasticsearch needs to know whether a value should support full-text search, exact matching, sorting, date queries, numeric range queries, and so on.

### Common Field Types

#### `FieldType.Text`

Use for full-text search.

Elasticsearch analyzes the value by breaking it into searchable terms.

Good for:

```text
Restaurant names
Descriptions
Review content
Searchable text
```

Example:

```java
@Field(type = FieldType.Text)
private String reviewContent;
```

`"Amazing grilled chicken"` can be searched as terms like `"amazing"`, `"grilled"`, and `"chicken"`.

#### `FieldType.Keyword`

Use for exact values.

Good for:

```text
IDs
Emails
Statuses
Usernames
Fields used for filtering or sorting
```

Example:

```java
@Field(type = FieldType.Keyword)
private String id;

@Field(type = FieldType.Keyword)
private String status;
```

#### Numeric Types

Use the appropriate number type for prices, ratings, counts, and scores.

```java
@Field(type = FieldType.Double)
private Double price;

@Field(type = FieldType.Integer)
private Integer rating;
```

#### `FieldType.Date`

Use for dates and times.

```java
@Field(type = FieldType.Date)
private LocalDateTime createdAt;
```

#### `FieldType.Boolean`

Use for true/false values.

```java
@Field(type = FieldType.Boolean)
private Boolean verified;
```

#### `FieldType.Object`

Use for a normal embedded object.

```java
@Field(type = FieldType.Object)
private Address address;
```

#### `FieldType.Nested`

Use for a list of objects when you need to query each object independently.

```java
@Field(type = FieldType.Nested)
private List<Review> reviews;
```

### `Object` vs `Nested`

`Object` flattens object fields during indexing.

`Nested` preserves the relationship between fields inside each object in a list.

Use this rule:

```text
Single embedded object                         -> Object
List of objects queried by their own fields    -> Nested
```

### Field Type Rule of Thumb

```text
Search box or fuzzy matching -> Text
Exact filter or sort         -> Keyword
Numbers                      -> Integer, Float, Double, etc.
Dates                        -> Date
True/false                   -> Boolean
Embedded object              -> Object
Queryable object list        -> Nested
```

## Lombok and Entity Classes

### Be Careful with `@Data`

`@Data` is convenient, but it can be risky on persistence entities.

It generates:

```text
@Getter
@Setter
@ToString
@EqualsAndHashCode
@RequiredArgsConstructor
```

The risky parts are usually `@EqualsAndHashCode`, `@ToString`, and unrestricted setters.

### Problem 1: `equals()` and `hashCode()`

By default, `@Data` uses all fields for `equals()` and `hashCode()`.

That can break when an entity ID changes after saving:

```java
User user = User.builder()
        .username("john")
        .build();

// user.id is null here
repository.save(user);
// user.id may now be assigned
```

If the object was already inside a `HashSet` or used as a key in a `HashMap`, changing the ID can change the hash code and make the object hard to find.

A safer approach is to control equality explicitly:

```java
@Getter
@Setter
@EqualsAndHashCode(onlyExplicitlyIncluded = true)
@Document(indexName = "users")
public class User {

    @Id
    @EqualsAndHashCode.Include
    private String id;

    @Field(type = FieldType.Keyword)
    private String username;
}
```

### Problem 2: `toString()` and Recursive Relationships

If two entities reference each other, generated `toString()` methods can call each other forever.

Example:

```java
public class Restaurant {
    private List<Review> reviews;
}

public class Review {
    private Restaurant restaurant;
}
```

`Restaurant.toString()` includes reviews. `Review.toString()` includes restaurant. This can cause a `StackOverflowError`.

Fix it by excluding relationship fields:

```java
@ToString.Exclude
private List<Review> reviews;
```

### Problem 3: Too Many Setters

`@Data` creates setters for every field. For entity classes, that can make it too easy for any part of the app to change important state.

Prefer explicit annotations:

```java
@Getter
@NoArgsConstructor
@AllArgsConstructor
@Builder
@EqualsAndHashCode(onlyExplicitlyIncluded = true)
@ToString
@Document(indexName = "users")
public class User {

    @Id
    @EqualsAndHashCode.Include
    private String id;

    @Field(type = FieldType.Keyword)
    private String username;

    @Field(type = FieldType.Text)
    private String givenName;
}
```

Use setters only where mutation is actually needed.

## Docker Workflow

### What Docker Runs in This Project

The Spring Boot app can run locally through Maven or IntelliJ.

Docker runs infrastructure services:

```text
Local machine
+-- Spring Boot app       -> runs with Maven or IntelliJ
+-- Docker containers
    +-- Elasticsearch     -> port 9200
    +-- Kibana            -> port 5601
    +-- Keycloak          -> port 9090
```

This avoids installing Elasticsearch, Kibana, or Keycloak directly on the machine.

### Daily Workflow

Start infrastructure first:

```bash
docker compose up -d
docker compose ps
```

Then start the Spring Boot app:

```bash
./mvnw spring-boot:run
```

End the session while keeping data:

```bash
docker compose stop
```

Start existing containers again later:

```bash
docker compose start
```

### `stop` vs `down`

```text
docker compose stop      -> stops containers, keeps containers and volumes
docker compose down      -> removes containers, keeps volumes
docker compose down -v   -> removes containers and volumes, wipes data
```

For normal development, use `stop` and `start`.

Use `down -v` only when you intentionally want to wipe local data.

### Useful Docker Commands

```bash
docker compose ps
docker ps
docker compose logs elasticsearch
docker compose logs -f elasticsearch
docker compose logs
docker compose restart elasticsearch
```

Check that Elasticsearch is responding:

```bash
curl http://localhost:9200
```

Common startup mistake:

```text
Starting Spring Boot before Docker services are ready.
```

Spring Boot may try to connect to Elasticsearch during startup. If Elasticsearch is not ready, the app can fail.

## Authentication with Keycloak

### Problem Keycloak Solves

Without Keycloak, the app would need to implement:

```text
Registration
Password hashing
Login/logout
JWT generation
Token validation
Refresh tokens
Roles
Password reset
Sessions
```

With Keycloak, the Spring Boot app mainly asks:

```text
Is this token valid?
What user does it represent?
What roles does the user have?
```

### Main Keycloak Concepts

#### Realm

A realm is an isolated auth space with its own users, roles, clients, and settings.

```text
master realm      -> Keycloak administration only
restaurant realm  -> app users and roles
```

Do not build the app inside the `master` realm.

#### Client

A client is an application allowed to interact with Keycloak.

Examples:

```text
restaurant-backend   -> Spring Boot API
restaurant-frontend  -> frontend app
```

Each client controls things like redirect URLs, token behavior, and access type.

#### User

A user is managed by Keycloak and has identity/auth information such as username, password, email, and roles.

### Login Flow

```text
1. User clicks login in the frontend
        |
2. Frontend redirects user to Keycloak login page
        |
3. User enters username and password in Keycloak
        |
4. Keycloak validates credentials
        |
5. Keycloak redirects back to frontend with an authorization code
        |
6. Frontend exchanges the code for tokens
        |
7. Frontend stores the access token
        |
8. Frontend calls Spring Boot with:
   Authorization: Bearer <token>
        |
9. Spring Security validates the token
        |
10. Valid token -> process request
    Invalid token -> return 401 Unauthorized
```

The Spring Boot app never sees the user's password.

### What's Inside a JWT?

A JWT is base64 encoded, not encrypted. Anyone with the token can decode and read its claims.

Example token payload:

```json
{
  "sub": "a1b2c3-uuid",
  "preferred_username": "john",
  "given_name": "John",
  "email": "john@example.com",
  "realm_access": {
    "roles": ["user", "restaurant_owner"]
  },
  "exp": 1713000000,
  "iss": "http://localhost:9090/realms/restaurant"
}
```

Important claims:

```text
sub   -> stable user ID
iss   -> token issuer
exp   -> expiry timestamp
roles -> authorization data
```

Spring Boot uses this setting to validate the issuer and discover Keycloak's signing keys:

```properties
spring.security.oauth2.resourceserver.jwt.issuer-uri=http://localhost:9090/realms/restaurant
```

### Roles and Authorization

Realm roles apply across the realm:

```text
user              -> normal logged-in user
restaurant_owner  -> can create/manage restaurants
admin             -> full access
```

Spring can protect endpoints with role checks:

```java
@PostMapping("/restaurants")
@PreAuthorize("hasRole('restaurant_owner')")
public RestaurantDto createRestaurant(@RequestBody RestaurantDto dto) {
    // Only restaurant owners
}
```

### Access Tokens vs Refresh Tokens

```text
Access token   -> short lived, sent with API requests
Refresh token  -> longer lived, used to get a new access token
```

Recommended pattern:

```text
Access token expires
        |
Frontend uses refresh token to get a new access token
        |
User continues without logging in again
        |
Refresh token eventually expires
        |
User logs in again
```

### How Spring Boot Validates a Token

```text
Request arrives with Authorization header
        |
Spring Security intercepts it
        |
Spring fetches Keycloak public keys
        |
Spring verifies the token signature
        |
Spring checks expiry
        |
Spring checks issuer
        |
Spring extracts claims and roles
        |
App handles the request
```

The app does not need to call Keycloak on every request. JWT signatures can be verified locally using Keycloak's public key.

### Accessing the Current User

```java
@GetMapping("/me")
public String getCurrentUser(@AuthenticationPrincipal Jwt jwt) {
    String userId = jwt.getSubject();
    String username = jwt.getClaimAsString("preferred_username");
    String givenName = jwt.getClaimAsString("given_name");

    return "Hello " + givenName;
}
```

`jwt.getSubject()` reads the `sub` claim. That value can be stored as the local user ID or as a `createdById` on app data.

## Keycloak Security Risks

### Exposed Admin Console

Do not expose the Keycloak admin console publicly in production.

Restrict it to an internal network, VPN, or admin-only environment.

### Weak Admin Credentials

Never deploy with default or weak admin credentials.

Use strong secrets and store them securely.

### No HTTPS in Production

HTTP is acceptable for local development only.

In production, use HTTPS so tokens cannot be intercepted in transit.

### Long Token Lifetimes

Long-lived access tokens increase risk if stolen.

Use short-lived access tokens, commonly around 5 to 15 minutes, and rely on refresh tokens for renewal.

### Missing Issuer Validation

The backend must validate that the token came from the expected Keycloak realm.

Use:

```properties
spring.security.oauth2.resourceserver.jwt.issuer-uri=http://localhost:9090/realms/restaurant
```

### Sensitive Data in JWT Claims

JWTs are readable by anyone who has the token.

Do not store passwords, card numbers, secrets, or highly sensitive personal data in JWT claims.

### Overprivileged Roles

Give users only the roles they need.

If a normal account has admin privileges and gets compromised, the damage is much worse.

### Old Keycloak Versions

Keep Keycloak updated, especially in production, because old versions may contain known security vulnerabilities.

## Invite-Only Users with Temporary Passwords

Keycloak supports invite-only flows using temporary passwords and required actions.

Typical flow:

```text
Admin creates user
        |
User receives temporary password
        |
User logs in
        |
Keycloak forces required actions:
  - Update password
  - Verify email, if enabled
  - Set up MFA, if enabled
        |
User completes setup
        |
User can access the app
```

In Keycloak, forcing a password reset is done with the `UPDATE_PASSWORD` required action.

### Creating Users Programmatically

There are three common approaches.

#### Option 1: Keycloak Admin REST API

Create a user through the admin API:

```http
POST http://localhost:9090/admin/realms/restaurant/users
Authorization: Bearer <admin-token>
Content-Type: application/json
```

```json
{
  "username": "john_doe",
  "email": "john@example.com",
  "enabled": true,
  "credentials": [
    {
      "type": "password",
      "value": "TempPass123!",
      "temporary": true
    }
  ],
  "requiredActions": ["UPDATE_PASSWORD"]
}
```

#### Option 2: Keycloak Admin Java Client

Use the Java client instead of raw HTTP:

```xml
<dependency>
    <groupId>org.keycloak</groupId>
    <artifactId>keycloak-admin-client</artifactId>
    <version>23.0.0</version>
</dependency>
```

Example:

```java
Keycloak keycloak = KeycloakBuilder.builder()
        .serverUrl("http://localhost:9090")
        .realm("master")
        .clientId("admin-cli")
        .username("admin")
        .password("admin")
        .build();

UserRepresentation user = new UserRepresentation();
user.setUsername("john_doe");
user.setEmail("john@example.com");
user.setEnabled(true);

CredentialRepresentation credential = new CredentialRepresentation();
credential.setType(CredentialRepresentation.PASSWORD);
credential.setValue("TempPass123!");
credential.setTemporary(true);

user.setCredentials(List.of(credential));
user.setRequiredActions(List.of("UPDATE_PASSWORD"));

keycloak.realm("restaurant").users().create(user);
```

#### Option 3: Bulk Import

For a one-time migration, export users to a Keycloak-compatible JSON file and import them.

### Invite-Only App Flow

```text
Existing list of users
        |
Spring Boot script or admin endpoint reads users
        |
App calls Keycloak Admin API
        |
Keycloak creates users with temporary passwords
        |
App emails credentials or invite link
        |
User logs in and must set a new password
```

## Photo Upload

Photo upload usually has two parts:

```text
File metadata      -> saved in the app database/search index
Actual file bytes  -> saved in storage
```

In this project, uploaded files are handled through a photo service and returned as Spring `Resource` objects.

Important design idea:

```text
Do not store large image bytes directly inside restaurant documents.
Store a photo ID or URL on the restaurant/photo entity.
Keep the actual file in file storage, object storage, or another dedicated storage system.
```

## Geolocation

The app can convert a restaurant address into coordinates using a geolocation service.

Current idea:

```text
Address -> geolocation provider -> latitude/longitude -> Elasticsearch GeoPoint
```

OpenStreetMap or a similar service can provide geocoding data.

In this project, `RestaurantServiceImpl` gets coordinates before saving the restaurant:

```java
Address address = request.getAddress();
GeoLocation geoLocation = geoLocationService.getlocate(address);
GeoPoint geoPoint = new GeoPoint(
        geoLocation.getLatitude(),
        geoLocation.getLongitude()
);
```

The result is stored as a `GeoPoint`, which makes location-based search possible later.

## Java Streams API

### What Streams Are

A Stream is a pipeline for processing data from a collection.

Streams are useful when you want to:

```text
Transform values
Filter values
Sort values
Count values
Join values into text
Group values
Convert entities to DTOs
```

Basic structure:

```java
collection.stream()
        .filter(...)
        .map(...)
        .toList();
```

A stream pipeline has three parts:

```text
Source                  -> where the data comes from
Intermediate operation  -> transforms or filters data
Terminal operation      -> produces the final result
```

Examples:

```text
Source                  -> photoIds.stream()
Intermediate operation  -> map(...), filter(...), sorted(...)
Terminal operation      -> toList(), collect(...), count()
```

### Example 1: Building Photo Entities

From `RestaurantServiceImpl`:

```java
List<Photo> photos = photoIds.stream()
        .map(photourl -> Photo.builder()
                .url(photourl)
                .uploadedDate(LocalDateTime.now())
                .build())
        .toList();
```

Here:

```text
photoIds           -> List<String>
photoIds.stream()  -> process each photo ID
map(...)           -> convert each String into a Photo
toList()           -> collect all Photo objects into a List<Photo>
```

Example input:

```java
List<String> photoIds = List.of("photo1.jpg", "photo2.jpg");
```

Output:

```java
List<Photo>
```

Each string becomes a `Photo` object:

```text
"photo1.jpg" -> Photo(url="photo1.jpg", uploadedDate=now)
```

Equivalent loop:

```java
List<Photo> photos = new ArrayList<>();

for (String photourl : photoIds) {
    Photo photo = Photo.builder()
            .url(photourl)
            .uploadedDate(LocalDateTime.now())
            .build();

    photos.add(photo);
}
```

The stream version is shorter and expresses the intent clearly:

```text
List of photo IDs -> List of Photo entities
```

### Example 2: Formatting Validation Errors

From `ErrorController`:

```java
String errorMessage = exception
        .getBindingResult()
        .getFieldErrors()
        .stream()
        .map(error -> error.getField() + ": " + error.getDefaultMessage())
        .collect(Collectors.joining(", "));
```

This turns many validation errors into one readable API message.

Example validation errors:

```text
name must not be blank
address must not be null
```

The stream transforms each error:

```java
error -> error.getField() + ": " + error.getDefaultMessage()
```

So a field error becomes:

```text
name: must not be blank
```

Then this line joins all messages:

```java
.collect(Collectors.joining(", "))
```

Final result:

```text
name: must not be blank, address: must not be null
```

### `Stream.map()` vs `Optional.map()`

In `PhotoController`, this code uses `Optional.map()`, not Streams API:

```java
return photoService.getPhotoAsResource(id)
        .map(photo -> ResponseEntity.ok()
                .contentType(
                        MediaTypeFactory.getMediaType(photo)
                                .orElse(MediaType.APPLICATION_OCTET_STREAM)
                )
                .header(HttpHeaders.CONTENT_DISPOSITION, "inline")
                .body(photo)
        )
        .orElse(ResponseEntity.notFound().build());
```

Difference:

```text
List.stream().map(...)  -> transforms each item in a collection
Optional.map(...)       -> transforms one value if it exists
```

The `PhotoController` flow is:

```text
Photo exists     -> return 200 OK with file
Photo missing    -> return 404 Not Found
```

### Common Stream Use Cases

Filter:

```java
List<Restaurant> italianRestaurants = restaurants.stream()
        .filter(restaurant -> restaurant.getCuisineType().equals("Italian"))
        .toList();
```

Map entities to DTOs:

```java
List<RestaurantDto> dtos = restaurants.stream()
        .map(restaurantMapper::toDto)
        .toList();
```

Count:

```java
long count = restaurants.stream()
        .filter(restaurant -> restaurant.getAverageRating() >= 4.0)
        .count();
```

Join strings:

```java
String names = restaurants.stream()
        .map(Restaurant::getName)
        .collect(Collectors.joining(", "));
```

Sort:

```java
List<Restaurant> sorted = restaurants.stream()
        .sorted(Comparator.comparing(Restaurant::getName))
        .toList();
```

Group:

```java
Map<String, List<Restaurant>> byCuisine = restaurants.stream()
        .collect(Collectors.groupingBy(Restaurant::getCuisineType));
```

### When to Use Streams

Use streams when the code is mainly about data transformation:

```text
List<A> -> List<B>
List<A> -> filtered List<A>
List<A> -> grouped Map<K, List<A>>
List<A> -> one joined String
```

Use a normal loop when:

```text
The logic has many side effects
The steps are complex
You need early break/continue behavior
The stream becomes harder to read than a loop
```

The goal is readability, not using streams everywhere.
