**The Purpose of application.properties**

The big idea is **separation of config from code** ? you can change ports, credentials, or URLs without touching your Java code. You can also have different versions of the file per environment (dev, staging, production).

## Lombok and Mapstruct Set up

Lombok + MapStruct Setup ? The Simple Mental Model
You're right that it feels like a lot of moving parts. Here's how to think about it cleanly, as a repeatable checklist you can apply to any project.

Why the complexity exists
There are three separate things involved:
Lombok       ? generates getters/setters/constructors at compile time
MapStruct    ? generates mapper implementations at compile time
The binding  ? enforces that Lombok runs BEFORE MapStruct
They're both annotation processors, so without the binding, their execution order is unpredictable and MapStruct may try to map a class that Lombok hasn't generated getters for yet.

The Checklist ? Every Time
Step 1: Pick versions
Don't overthink compatibility. Just use:

Latest stable Lombok (check projectlombok.org)
Latest stable MapStruct (check mapstruct.org)
lombok-mapstruct-binding is always 0.2.0 ? this never changes

xml<properties>
<lombok.version>1.18.36</lombok.version>
<org.mapstruct.version>1.6.3</org.mapstruct.version>
</properties>
They don't have a strict compatibility matrix ? latest of each always works together.

Step 2: Add runtime dependencies
These are what your code actually uses at compile/runtime:
xml<dependency>
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

Step 3: Configure the compiler plugin ? always this exact structure
xml<plugin>
<groupId>org.apache.maven.plugins</groupId>
<artifactId>maven-compiler-plugin</artifactId>
<configuration>
<annotationProcessorPaths>
<!-- 1. Lombok first ? generates getters/setters -->
<path>
<groupId>org.projectlombok</groupId>
<artifactId>lombok</artifactId>
<version>${lombok.version}</version>
</path>
<!-- 2. MapStruct ? generates mapper implementations -->
<path>
<groupId>org.mapstruct</groupId>
<artifactId>mapstruct-processor</artifactId>
<version>${org.mapstruct.version}</version>
</path>
<!-- 3. Binding ? enforces the order above -->
```
<path>
<groupId>org.projectlombok</groupId>
<artifactId>lombok-mapstruct-binding</artifactId>
<version>0.2.0</version>
</path>
</annotationProcessorPaths>
</configuration>
</plugin>
```
Only ever declare this plugin once. That was the root of your earlier error.

The One-Liner to Remember

Dependencies go in <dependencies>, processors go in the compiler plugin's <annotationProcessorPaths>, Lombok always before MapStruct, binding always last. One plugin block, never two.

### Module 2- Domain Modelling

**What Keycloak Does**
Keycloak is an Identity Provider (IdP) — it handles everything authentication related:

User registration → Keycloak
Login / logout   → Keycloak
Passwords        → Keycloak
Tokens (JWT)     → Keycloak
Roles            → Keycloak

When a user logs in, Keycloak issues a JWT token that your Spring Boot app receives and validates. 
That token already contains user information inside it:
```{
"sub": "a1b2c3-uuid",
"preferred_username": "john_doe",
"given_name": "John",
"email": "john@example.com",
"roles": ["user", "admin"]
}
```

**So Do You Need a User Entity?**
It depends on what your app needs to store about users.

**When you DON'T need a User entity**
If your app only needs to know who is making a request and nothing else — just read the JWT token and use the data directly. No database entry needed.

Examples:
* A simple read-only API where you just check if someone is logged in
* An app where user data lives entirely in Keycloak and you never store anything extra

**When you DO need a User entity (your case)**
For a restaurant review app, you almost certainly need one. Here's why:

Review → belongs to a User
Restaurant → created by a User
Rating    → submitted by a User
You need to link app data back to a user. Keycloak doesn't store your reviews or relationships — your database does. So you need a local reference.

**The Right Pattern — Thin User Entity**
You don't replicate everything Keycloak stores. You just keep a lightweight local reference that links your app data to the Keycloak user:
```
@Data
@AllArgsConstructor
@NoArgsConstructor
@Builder
@Document(indexName = "users")
public class User {

    @Id
    private String id;          // same as Keycloak's "sub" (subject/UUID)

    @Field(type = FieldType.Keyword)
    private String username;    // synced from Keycloak token

    @Field(type = FieldType.Text)
    private String givenName;   // synced from Keycloak token
}
```

The id here matches the sub claim in the JWT — that's the bridge between Keycloak and your app.

**How It Flows at Runtime**
```
User logs in via Keycloak
↓
Keycloak issues JWT token
↓
User makes request to your Spring Boot API (token in header)
↓
Spring Security validates the token
↓
Your app extracts the "sub" (user ID) from the token
↓
Looks up or creates the User in Elasticsearch
↓
Links reviews/ratings to that User
```

The first time a user hits your API, you create their local record. After that you just look them up by the Keycloak sub ID.

#### Keycloak Traps and Vulnerabilities to Watch Out For

1. Leaving the admin console exposed
   By default Keycloak's admin panel runs on the same port as your app. In production, never expose the admin console publicly:
# Restrict admin to internal network only
http://localhost:8080/admin  ← should never be publicly accessible

2. Using the master realm for your app
   Keycloak has a master realm by default — it's for administering Keycloak itself. A common beginner mistake is building your app inside it. Always create a separate realm for your app:
   master realm     → Keycloak administration only
   restaurant realm → your actual app users

3. Not validating JWT properly
   Spring Boot with oauth2-resource-server handles this for you, but you must configure it correctly in application.properties:
   propertiesspring.security.oauth2.resourceserver.jwt.issuer-uri=http://localhost:8180/realms/restaurant
   If this is wrong or missing, tokens won't be validated properly and your app becomes insecure.

4. Storing sensitive data in JWT claims
   JWT tokens are base64 encoded, not encrypted — anyone can decode them. Never put passwords, card numbers, or sensitive personal data in token claims. Stick to IDs, usernames, and roles.

5. Token expiry and refresh mishandling
   If you set access tokens to never expire or with very long lifetimes, a stolen token is valid forever. Recommended approach:
   Access token  → short lived (5-15 minutes)
   Refresh token → longer lived (hours/days)

6. Running Keycloak without HTTPS in production
   In dev, HTTP is fine. In production, always run Keycloak behind HTTPS. Tokens sent over plain HTTP can be intercepted.

### ElasticSearch

**What @Field does**

@Field comes from Spring Data Elasticsearch. It's the equivalent of defining your column type in a SQL schema, but for Elasticsearch.

**Why it matters**
Remember the inverted index concept from earlier? Elasticsearch needs to know how to index each field — should it be searchable? Sortable? Exact match only? @Field tells it exactly that.

**The main FieldType options and what they mean:**
1. FieldType.Text

* Used for full-text search fields
* Elasticsearch analyzes the content — breaks it into tokens, lowercases it, removes stop words
* Good for: descriptions, review content, restaurant names you want fuzzy search on

```@Field(type = FieldType.Text)
private String reviewContent;   // "Amazing grilled chicken" → ["amazing", "grilled", "chicken"]
```

2. FieldType.Keyword

* Used for exact match fields — stored as-is, no analysis
* Good for: IDs, emails, status fields, anything you filter/sort by exactly

```@Field(type = FieldType.Keyword)
private String id;           // must match exactly "a1b2c3", not analyzed
private String status;       // "ACTIVE", "INACTIVE" — exact values
```

3. **FieldType.Integer / FieldType.Double / FieldType.Float**

* Numeric fields for ratings, prices, counts

```@Field(type = FieldType.Double)
private Double price;

@Field(type = FieldType.Integer)
private Integer rating;      // 1-5 stars
```

5. FieldType.Date

* For date/time fields

```
@Field(type = FieldType.Date)
private LocalDateTime createdAt;
```

6. FieldType.Boolean
```
@Field(type = FieldType.Boolean)
private Boolean isVerified;
```

7. FieldType.Object

For nested plain objects (not separately indexed)

```@Field(type = FieldType.Object)
private Address address;     // embedded object
```
8. FieldType.Nested

For collections of objects where you need to query inside each object independently

```
@Field(type = FieldType.Nested)
private List<Review> reviews;   // each review is queryable on its own fields
```
The difference between Object and Nested is subtle but important — Nested preserves the relationship between fields inside each object when querying, Object flattens them.

How it applies to your User entity
```
@Document(indexName = "users")
public class User {

    @Id
    private String id;                        // Keycloak sub — exact match

    @Field(type = FieldType.Keyword)
    private String username;                  // exact match, filter by username

    @Field(type = FieldType.Text)
    private String givenName;                 // searchable, "John" matches "john"
}
```

A useful rule of thumb:

Search box / fuzzy matching → Text
Filter / sort / exact match → Keyword
Numbers → appropriate numeric type
Dates → Date

### Problems with using @Data with Persistence Frameworks

@Data is convenient but it has some real problems when used with persistence frameworks like JPA or Spring Data Elasticsearch.

**What @Data generates**
@Data is a Lombok shortcut that generates:

* @Getter — getters for all fields
* @Setter — setters for all fields
* @ToString — string representation
* @EqualsAndHashCode — equals and hashCode based on all fields
* @RequiredArgsConstructor — constructor for final/@NonNull fields

The problems come specifically from @EqualsAndHashCode and @ToString.

**Problem 1: @EqualsAndHashCode — The Dangerous One**
By default @Data generates equals() and hashCode() using all fields in the class.
Why this breaks with Elasticsearch/JPA:
When you save an entity, the id starts as null, then gets assigned after saving:
```
User user = User.builder()
.username("john")
.build();
// At this point: id = null

repository.save(user);
// Now: id = "a1b2c3-uuid"
```
Because the id changed, the hashCode changes too. If you stored this object in a HashSet or HashMap before saving, it's now in the wrong bucket — effectively lost. This causes subtle, hard-to-find bugs.
The fix:
Use @EqualsAndHashCode explicitly, based only on id:
```
@Data
@EqualsAndHashCode(onlyExplicitlyIncluded = true)
@Document(indexName = "users")
public class User {

    @Id
    @EqualsAndHashCode.Include   // only use id for equals/hashCode
    private String id;

    @Field(type = FieldType.Keyword)
    private String username;

    @Field(type = FieldType.Text)
    private String givenName;
}
```

**Problem 2: @ToString — Causes Infinite Loops**
If your entities have bidirectional relationships, @ToString will cause a StackOverflowError at runtime.
For example in a review app:
```
// Restaurant has reviews
@Document(indexName = "restaurants")
@Data
public class Restaurant {
private String id;
private List<Review> reviews;   // toString() calls Review.toString()
}

// Review has a restaurant reference
@Document(indexName = "reviews")
@Data
public class Review {
private String id;
private Restaurant restaurant;  // toString() calls Restaurant.toString() → infinite loop 💥
}
```
The fix:
Exclude relationship fields from @ToString:
```
@Data
@Document(indexName = "restaurants")
public class Restaurant {
private String id;

    @ToString.Exclude            // breaks the cycle
    private List<Review> reviews;
}
```

**Problem 3: @Data is Too Broad for Entities**
Entities should generally be not freely mutable — you don't want just anything setting any field at any time. @Data generates setters for everything, which makes your entity wide open.
A cleaner approach for entities is to be explicit:

```
@Getter                          // getters yes
@NoArgsConstructor               // needed by Elasticsearch/JPA for deserialization
@AllArgsConstructor              // for builder/constructor use
@Builder                         // builder pattern
@EqualsAndHashCode(onlyExplicitlyIncluded = true)
@ToString(exclude = {"reviews"}) // safe toString
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
No @Setter — if you need to update a field, do it through a meaningful method.

## FAQ
**1. Why String for id and not UUID or Long?**

In a SQL/JPA world you'd typically use Long (auto-increment) or UUID. In Elasticsearch the reason is different:
   
Elasticsearch natively uses string IDs. Every document in Elasticsearch has a _id field that is always a string internally. When you use Long or UUID, Spring Data Elasticsearch converts it to a string anyway under the hood.
   
The bigger reason in your specific app is Keycloak. Your User id comes directly from Keycloak's sub claim which looks like:
`   "a3f2b1c4-9d8e-4f7a-b2c1-3e4d5f6a7b8c"
`  
That's already a string. If you used UUID as a Java type you'd have to constantly convert between UUID and String when reading from the JWT token. Using String keeps it simple and consistent across all entities.
   
For Restaurant, Review etc — Elasticsearch will auto-generate a string ID if you don't provide one, similar to how SQL auto-increments a Long. So String just works naturally with Elasticsearch.

**2. Why no Review field in Restaurant?**
   
This is an important design decision. Think about what happens if you embed reviews inside Restaurant:
   `// If you did this
   @Field(type = FieldType.Nested)
   private List<Review> reviews = new ArrayList<>();`
   
A popular restaurant could have thousands of reviews. Every time you fetch that restaurant you'd load thousands of reviews with it — even if you just wanted the restaurant's name and address. That's wasteful and slow.
   
The better design is to let Review reference the Restaurant:
   
   `// Inside Review entity
   @Field(type = FieldType.Keyword)
   private String restaurantId;    // Review knows which restaurant it belongs to
   `
Then when you need reviews for a restaurant, you query the reviews index separately:
   `GET /reviews/_search
   { "query": { "term": { "restaurantId": "abc123" } } }`

This is the same reason you don't embed all orders inside a Customer in a database — you keep them separate and reference by ID.

# Module 3 : Authentication

## Docker Workflow for a Spring Boot Project
Here's the mental model and workflow you should follow.

**What Docker is doing in your project**

Your Spring Boot app itself runs on your machine (via ./mvnw or IntelliJ). Docker runs the infrastructure your app depends on — databases, search engines, auth servers. You never need to install Elasticsearch, Keycloak, or PostgreSQL directly on your machine.

```
Your machine:
├── Spring Boot app  ← runs locally via Maven/IntelliJ
└── Docker
├── Elasticsearch  ← port 9200
├── Kibana         ← port 5601
└── Keycloak       ← port 9090
```

**The Daily Workflow**

Starting your dev session:
```
# 1. Start infrastructure
docker compose up -d

# 2. Verify everything is healthy
docker compose ps

# 3. Start your Spring Boot app
./mvnw spring-boot:run
# or just run it from IntelliJ
```
Ending your dev session:
```
# Stop containers but KEEP data (volumes preserved)
docker compose stop

# Next time, just start them again
docker compose start
```
**The difference between stop and down:**
```
docker compose stop   → pauses containers, data is preserved
docker compose down   → removes containers, data is preserved (volumes survive)
docker compose down -v → removes containers AND volumes (wipes all data) ← be careful
```
For day-to-day work, use stop and start. Use down only when you want a clean slate.

### Useful Commands to Know

**See what's running:**

```
docker compose ps          # containers in this project
docker ps                  # ALL containers on your machine
```
**Read logs when something isn't working:**
```
docker compose logs elasticsearch        # logs for one service
docker compose logs -f elasticsearch     # follow logs in real time
docker compose logs                      # logs for everything
```
**Check if Elasticsearch is actually ready (not just "running" but accepting connections):**

`curl http://localhost:9200
`
**Restart a single service without touching the others:**
`docker compose restart elasticsearch
`
**Completely wipe and start fresh (when things are really broken):**
```
docker compose down -v      # removes containers AND data volumes
docker compose up -d        # starts fresh with empty data
```
**The Most Common Mistake**
Running ./mvnw clean install or starting your app before Docker containers are up. Spring Boot tries to connect to Elasticsearch immediately on startup — if it's not running yet, it fails.
Always start Docker first, app second. A quick check before starting your app:
```
docker compose ps   # are they all "running"?
curl http://localhost:9200  # is Elasticsearch actually responding?
```
