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
 *   <li>static of() method to create a new builder</li>
 *   <li>static from(record) method to create a builder from an existing record</li>
 *   <li>setXxx() setter methods for all non-collection and non-map fields</li>
 *   <li>addXxx()/addAllXxx() adder methods for collection fields</li>
 *   <li>putXxx()/putAllXxx() putter methods for map fields</li>
 *   <li>getXxx() getter methods for all fields</li>
 *   <li>hasXxx() presence checker methods for all fields</li>
 *   <li>clearXxx() clearer methods for all fields</li>
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
