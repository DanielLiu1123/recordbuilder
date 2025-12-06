package recordbuilder;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.jspecify.annotations.Nullable;

/**
 * Test record for RecordBuilder annotation processor.
 *
 * @author Freeman
 */
@RecordBuilder
public record Everything(
        // all primitive types
        byte byte_,
        @Nullable Short short_,
        int int_,
        long long_,
        float float_,
        Double double_,
        char char_,
        Boolean boolean_,

        // reference types
        String string,
        @Nullable String nullableString,
        LocalDate localDate,
        @Nullable LocalDate nullableLocalDate,
        Everything.JavaRecord javaRecord,
        Everything.@Nullable JavaRecord nullableJavaRecord,
        Everything.JavaClass javaClass,
        Everything.@Nullable JavaClass nullableJavaClass,

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
        @Nullable Set<String> nullableSetString,
        Set<@Nullable String> setNullableString,
        Map<String, Integer> mapStringInteger,
        @Nullable Map<String, Integer> nullableMapStringInteger,
        Map<@Nullable String, Integer> mapNullableStringInteger,
        Map<String, @Nullable Integer> mapStringNullableInteger,
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
        @Nullable HashSet<String> nullableHashSetString) {

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
