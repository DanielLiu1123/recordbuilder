package recordbuilder;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Annotation to generate a builder for a Java record.
 *
 * <p>
 * The generated builder will include:
 * <ul>
 *   <li>static builder() method to create a new builder</li>
 *   <li>static builder(source) method to create a builder from an existing record</li>
 *   <li>setXxx() setter methods for all fields</li>
 *   <li>getXxx() getter methods for all fields</li>
 *   <li>hasXxx() presence checker methods for all fields</li>
 *   <li>clearXxx() clearer methods for all fields</li>
 *   <li>merge(source) method to merge values from a record</li>
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
