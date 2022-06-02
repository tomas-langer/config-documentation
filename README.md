# Generate documentation from configuration metadata

Add to classpath of this module any module that you want to document (to `pom.xml` `dependencies` section)

## Build and run

With JDK11+

The following parameters are expected:
- module_name - name of a module, or `*` to process all modules on classpath (such as `io.helidon.common.configurable`)
- target_directory - directory where modules will be generated, such as `/projects/helidon/docs/shared/config`

```bash
mvn package
java -jar target/helidon-config-metadata.jar $module_name $target_directory
```

This would generate the following (using the sample parameter values and with current Helidon code):

- File `io.helidon.common.configurable.LruCache.Builder.adoc`
- File `io.helidon.common.configurable.Resource.adoc`
- File `io.helidon.common.configurable.ScheduledThreadPoolSupplier.adoc`
- File `io.helidon.common.configurable.ThreadPoolSupplier.adoc`

