package recordbuilder;

import com.palantir.javapoet.ClassName;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Hashtable;
import java.util.IdentityHashMap;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.LinkedList;
import java.util.List;
import java.util.Properties;
import java.util.Set;
import java.util.Stack;
import java.util.TreeMap;
import java.util.TreeSet;
import java.util.Vector;
import java.util.WeakHashMap;
import javax.lang.model.element.TypeElement;
import javax.lang.model.type.DeclaredType;
import javax.lang.model.type.TypeKind;
import javax.lang.model.type.TypeMirror;

/**
 * Helper class for working with collection and map types.
 *
 * @author Freeman
 */
final class CollectionHelper {

    private CollectionHelper() {
        throw new AssertionError("No instances");
    }

    /**
     * Gets the appropriate implementation class for a collection type.
     * Returns the concrete class if the type is already concrete, otherwise
     * returns the default implementation for the interface.
     *
     * @param type the collection type
     * @return the implementation class to use
     */
    static Class<?> getCollectionImplClass(TypeMirror type) {
        if (type.getKind() != TypeKind.DECLARED) {
            return ArrayList.class;
        }

        TypeElement typeElement = (TypeElement) ((DeclaredType) type).asElement();
        String qualifiedName = typeElement.getQualifiedName().toString();

        return switch (qualifiedName) {
            case "java.util.Set" -> HashSet.class;
            case "java.util.HashSet" -> HashSet.class;
            case "java.util.LinkedHashSet" -> LinkedHashSet.class;
            case "java.util.TreeSet" -> TreeSet.class;
            case "java.util.List" -> ArrayList.class;
            case "java.util.ArrayList" -> ArrayList.class;
            case "java.util.LinkedList" -> LinkedList.class;
            case "java.util.ArrayDeque" -> ArrayDeque.class;
            case "java.util.Vector" -> Vector.class;
            case "java.util.Stack" -> Stack.class;
            case "java.util.concurrent.CopyOnWriteArrayList" -> java.util.concurrent.CopyOnWriteArrayList.class;
            case "java.util.concurrent.CopyOnWriteArraySet" -> java.util.concurrent.CopyOnWriteArraySet.class;
            case "java.util.concurrent.ConcurrentSkipListSet" -> java.util.concurrent.ConcurrentSkipListSet.class;
            default -> ArrayList.class; // Default to ArrayList for other Collection types
        };
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

    /**
     * Gets the appropriate implementation class for a map type.
     * Returns the concrete class if the type is already concrete, otherwise
     * returns the default implementation (HashMap).
     *
     * @param type the map type
     * @return the implementation class to use
     */
    static Class<?> getMapImplClass(TypeMirror type) {
        if (type.getKind() != TypeKind.DECLARED) {
            return HashMap.class;
        }

        TypeElement typeElement = (TypeElement) ((DeclaredType) type).asElement();
        String qualifiedName = typeElement.getQualifiedName().toString();

        return switch (qualifiedName) {
            case "java.util.Map" -> HashMap.class;
            case "java.util.HashMap" -> HashMap.class;
            case "java.util.LinkedHashMap" -> LinkedHashMap.class;
            case "java.util.TreeMap" -> TreeMap.class;
            case "java.util.WeakHashMap" -> WeakHashMap.class;
            case "java.util.IdentityHashMap" -> IdentityHashMap.class;
            case "java.util.Hashtable" -> Hashtable.class;
            case "java.util.Properties" -> Properties.class;
            case "java.util.concurrent.ConcurrentHashMap" -> java.util.concurrent.ConcurrentHashMap.class;
            case "java.util.concurrent.ConcurrentSkipListMap" -> java.util.concurrent.ConcurrentSkipListMap.class;
            default -> HashMap.class; // Default to HashMap for other Map types
        };
    }
}
