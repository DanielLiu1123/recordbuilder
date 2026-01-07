package recordbuilder;

import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMap;
import com.google.common.collect.ImmutableSet;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.CopyOnWriteArrayList;
import org.jspecify.annotations.Nullable;

/**
 * Test record for RecordBuilder annotation processor.
 *
 * @author Freeman
 */
@RecordBuilder
public record Everything<A, B extends Number>(
        // all primitive types
        byte byte_,
        @Nullable Short short_,
        int int_,
        long long_,
        float float_,
        Double double_,
        char char_,
        Boolean boolean_,

        // singular types
        String string,
        @Nullable String nullableString,
        LocalDate localDate,
        @Nullable LocalDate nullableLocalDate,
        Everything.JavaRecord javaRecord,
        Everything.@Nullable JavaRecord nullableJavaRecord,
        Everything.JavaClass javaClass,
        Everything.@Nullable JavaClass nullableJavaClass,

        // array types
        int[] intArray,
        int @Nullable [] intNullableArray,
        @Nullable String[] nullableStringArray,
        String @Nullable [] stringNullableArray,
        @Nullable String @Nullable [] nullableStringNullableArray,
        Everything.JavaRecord[] javaRecordArray,
        Everything.@Nullable JavaRecord @Nullable [] nullableJavaRecordNullableArray,
        Everything.JavaClass[] javaClassArray,
        Everything.@Nullable JavaClass[] nullableJavaClassArray,

        // collection types
        List<String> listString,
        @Nullable List<String> nullableListString,
        List<@Nullable String> listNullableString,
        List<Everything.JavaRecord> listJavaRecord,
        List<Everything.@Nullable JavaRecord> listNullableJavaRecord,
        List<Everything.JavaClass> listJavaClass,
        List<Everything.@Nullable JavaClass> listNullableJavaClass,
        List<Map<Everything.@Nullable JavaClass, Everything.@Nullable JavaRecord>>
                listMapNullableJavaClassNullableJavaRecord,
        Set<String> setString,
        List list,
        @Nullable List nullableList,
        List<?> listWildcard,
        List<? extends @Nullable Object> listWildcardExtendsNullableObject,
        List<? extends Number> listWildcardExtendsNumber,
        CopyOnWriteArrayList<Object> copyOnWriteArrayListObject,
        CopyOnWriteArrayList<@Nullable Object> nullableCopyOnWriteArrayListObject,
        Set set,
        @Nullable Set nullableSet,
        @Nullable Set<String> nullableSetString,
        Set<@Nullable String> setNullableString,
        Map<String, Integer> mapStringInteger,
        @Nullable Map<String, Integer> nullableMapStringInteger,
        Map<@Nullable String, Integer> mapNullableStringInteger,
        Map<String, @Nullable Integer> mapStringNullableInteger,
        Map map,
        @Nullable Map nullableMap,
        Map<?, String> mapWildcardString,
        Map<String, ? extends Object> mapStringWildcardExtendsObject,
        Map<? extends String, ? extends @Nullable Object> mapWildcardExtendsStringWildcardExtendsObject,
        Map<String, List<? extends Number>> mapStringListWildcardExtendsNumber,
        Map<@Nullable String, @Nullable Integer> mapNullableStringNullableInteger,
        Map<String, List<@Nullable String>> mapStringListNullableString,
        Map<String, Everything.@Nullable JavaRecord> mapStringNullableJavaRecord,
        Map<String, List<Everything.@Nullable JavaRecord>> mapStringListNullableJavaRecord,
        Map<String, Map<Everything.@Nullable JavaClass, Everything.@Nullable JavaRecord>>
                mapStringMapNullableJavaClassNullableJavaRecord,
        ArrayList<String> arrayListString,
        @Nullable ArrayList<String> nullableArrayListString,
        HashMap<String, Integer> hashMapStringInteger,
        @Nullable HashMap<String, Integer> nullableHashMapStringInteger,
        HashSet<String> hashSetString,
        @Nullable HashSet<String> nullableHashSetString,

        // typevar
        A typeVarA,
        @Nullable A nullableTypeVarA,
        B typeVarB,
        @Nullable B nullableTypeVarB,
        List<A> listTypeVarA,
        List<@Nullable A> listNullableTypeVarA,
        Map<A, B> mapTypeVarATypeVarB,
        Map<@Nullable A, @Nullable B> mapNullableTypeVarANullableTypeVarB,
        Map<A, List<B>> mapTypeVarAListTypeVarB,

        // guava collection
        ImmutableList<String> immutableListString,
        @Nullable ImmutableList<String> nullableImmutableListString,
        ImmutableMap<String, Integer> immutableMapStringInteger,
        @Nullable ImmutableMap<String, Integer> nullableImmutableMapStringInteger,
        ImmutableSet<String> immutableSetString,
        @Nullable ImmutableSet<String> nullableImmutableSetString,

        // unknown collection types
        Iterable<Object> iterableObject,
        @Nullable Iterable<Object> nullableIterableObject) {

    // provide default values for fields
    public Everything {
        boolean_ = boolean_ != null ? boolean_ : false;
        string = string != null ? string : "";
    }

    record JavaRecord(String field) {}

    static class JavaClass {
        public String field;
    }
}
