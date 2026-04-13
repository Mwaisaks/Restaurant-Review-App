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
<path>
<groupId>org.projectlombok</groupId>
<artifactId>lombok-mapstruct-binding</artifactId>
<version>0.2.0</version>
</path>
</annotationProcessorPaths>
</configuration>
</plugin>
Only ever declare this plugin once. That was the root of your earlier error.

The One-Liner to Remember

Dependencies go in <dependencies>, processors go in the compiler plugin's <annotationProcessorPaths>, Lombok always before MapStruct, binding always last. One plugin block, never two.