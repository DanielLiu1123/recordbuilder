package recordbuilder;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Annotation to generate a builder for a Java record.
 *
 * <pre>{@code
 * import recordbuilder.RecordBuilder;
 * import org.jspecify.annotations.Nullable;
 *
 * @RecordBuilder
 * public record User(
 *     String name,
 *     Integer age,
 *     @Nullable String email
 * ) {}
 * }</pre>
 *
 * Generates class:
 *
 * <pre>{@code
 * import org.jspecify.annotations.Nullable;
 *
 * @Generated(
 *     value = "recordbuilder.RecordBuilderProcessor",
 *     date = "...",
 * )
 * public final class UserBuilder {
 *     private String _name;
 *     private Integer _age;
 *     private @Nullable String _email;
 *
 *     private UserBuilder() {}
 *
 *     // Factory methods
 *     public static UserBuilder builder() { ... }
 *     public static UserBuilder builder(User source) { ... }
 *
 *     // Merge method
 *     public UserBuilder merge(User source) { ... }
 *
 *     // Setter methods
 *     public UserBuilder setName(String name) { ... }
 *     public UserBuilder setAge(Integer age) { ... }
 *     public UserBuilder setEmail(@Nullable String email) { ... }
 *
 *     // Has methods
 *     public boolean hasName() { ... }
 *     public boolean hasAge() { ... }
 *     public boolean hasEmail() { ... }
 *
 *     // Getter methods
 *     public String getName() { ... }
 *     public Integer getAge() { ... }
 *     public @Nullable String getEmail() { ... }
 *
 *     // Clear methods
 *     public UserBuilder clearName() { ... }
 *     public UserBuilder clearAge() { ... }
 *     public UserBuilder clearEmail() { ... }
 *
 *     // Build method
 *     public User build() { ... }
 *
 *     // toString
 *     @Override
 *     public String toString() { ... }
 * }
 * }</pre>
 *
 * <p>
 * The generated builder will include:
 * <ul>
 *   <li>static builder() method to create a new builder</li>
 *   <li>static builder(source) method to create a builder from an existing record</li>
 *   <li>merge(source) method to merge values from a record</li>
 *   <li>setXxx() setter methods for all fields</li>
 *   <li>hasXxx() presence checker methods for all fields</li>
 *   <li>getXxx() getter methods for all fields</li>
 *   <li>clearXxx() clearer methods for all fields</li>
 *   <li>toString() method</li>
 * </ul>
 *
 * <p>
 * Non-null validation is applied by default unless the field is annotated with @Nullable.
 *
 * @author Freeman
 */
@Target(ElementType.TYPE)
@Retention(RetentionPolicy.SOURCE)
public @interface RecordBuilder {}
