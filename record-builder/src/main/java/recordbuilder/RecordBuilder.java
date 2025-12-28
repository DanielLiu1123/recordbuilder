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
 *     @Nullable String email,
 *     List<String> roles,
 *     Map<String, String> attributes
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
 *     private List<String> _roles;
 *     private Map<String, String> _attributes;
 *
 *     private int _presenceMask0_;
 *
 *     private UserBuilder() {}
 *
 *     // Factory methods to create a new builder
 *     public static UserBuilder builder() { ... }
 *     public static UserBuilder builder(User prototype) { ... }
 *
 *     // Merge method to merge non-null fields from another record
 *     public UserBuilder merge(User other) { ... }
 *
 *     // Setter methods for singular fields
 *     public UserBuilder setName(String name) { ... }
 *     public UserBuilder setAge(Integer age) { ... }
 *     public UserBuilder setEmail(@Nullable String email) { ... }
 *
 *     // Adder methods for Collection
 *     public UserBuilder addRole(String value) { ... }
 *     public UserBuilder addAllRoles(Iterable<? extends String> values) { ... }
 *
 *     // Putter methods for Map
 *     public UserBuilder putAttribute(String key, String value) { ... }
 *     public UserBuilder putAllAttributes(Map<String, String> values) { ... }
 *
 *     // Has methods to check if field was set
 *     public boolean hasName() { ... }
 *     public boolean hasAge() { ... }
 *     public boolean hasEmail() { ... }
 *     public boolean hasRoles() { ... }
 *     public boolean hasAttributes() { ... }
 *
 *     // Getter methods
 *     public String getName() { ... }
 *     public Integer getAge() { ... }
 *     public @Nullable String getEmail() { ... }
 *     public List<String> getRoles() { ... }
 *     public Map<String, String> getAttributes() { ... }
 *
 *     // Clear methods to reset fields and mark as unset
 *     public UserBuilder clearName() { ... }
 *     public UserBuilder clearAge() { ... }
 *     public UserBuilder clearEmail() { ... }
 *     public UserBuilder clearRoles() { ... }
 *     public UserBuilder clearAttributes() { ... }
 *     public UserBuilder clear() { ... }
 *
 *     // Build method
 *     public User build() { ... }
 *
 *     // toString method to show only explicitly set fields
 *     @Override
 *     public String toString() { ... }
 * }
 * }</pre>
 *
 * <p>
 * The generated builder will include:
 * <ul>
 *   <li>builder() method to create a new builder</li>
 *   <li>builder(prototype) method to create a builder from an existing record</li>
 *   <li>merge(other) method to merge non-null fields from another record</li>
 *   <li>setXxx() methods for singular fields</li>
 *   <li>addXxx()/addAllXxx() methods for Collection fields</li>
 *   <li>putXxx()/putAllXxx() methods for Map fields</li>
 *   <li>hasXxx() methods to check if the field was set</li>
 *   <li>getXxx() methods for all fields</li>
 *   <li>clearXxx() methods to reset fields and mark as unset</li>
 *   <li>clear() method to reset all fields</li>
 *   <li>build() method to create the record</li>
 *   <li>toString() method to show only explicitly set fields</li>
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
