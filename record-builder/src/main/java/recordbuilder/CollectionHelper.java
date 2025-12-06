package recordbuilder;

import com.palantir.javapoet.ClassName;
import java.util.List;
import java.util.Set;
import javax.lang.model.element.TypeElement;
import javax.lang.model.type.DeclaredType;
import javax.lang.model.type.TypeKind;
import javax.lang.model.type.TypeMirror;

/**
 * Helper class for working with collection types.
 *
 * @author Freeman
 */
final class CollectionHelper {

    private CollectionHelper() {
        throw new AssertionError("No instances");
    }

    /**
     * Gets the appropriate interface class for a collection type.
     * Returns either List or Set based on the collection type.
     *
     * @param type the collection type
     * @return the interface class (List or Set)
     */
    static ClassName getCollectionInterfaceClass(TypeMirror type) {
        if (type.getKind() != TypeKind.DECLARED) {
            return ClassName.get(List.class);
        }

        TypeElement typeElement = (TypeElement) ((DeclaredType) type).asElement();
        String qualifiedName = typeElement.getQualifiedName().toString();

        // Check if it's a Set type
        if (qualifiedName.equals("java.util.Set")
                || qualifiedName.equals("java.util.HashSet")
                || qualifiedName.equals("java.util.LinkedHashSet")
                || qualifiedName.equals("java.util.TreeSet")
                || qualifiedName.equals("java.util.concurrent.CopyOnWriteArraySet")
                || qualifiedName.equals("java.util.concurrent.ConcurrentSkipListSet")) {
            return ClassName.get(Set.class);
        }

        // Check if it's a List type
        if (qualifiedName.equals("java.util.List")
                || qualifiedName.equals("java.util.ArrayList")
                || qualifiedName.equals("java.util.LinkedList")
                || qualifiedName.equals("java.util.Vector")
                || qualifiedName.equals("java.util.Stack")
                || qualifiedName.equals("java.util.concurrent.CopyOnWriteArrayList")) {
            return ClassName.get(List.class);
        }

        // Default to List for other Collection types (including Queue, Deque, etc)
        return ClassName.get(List.class);
    }
}
