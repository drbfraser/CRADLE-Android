package com.cradletrial.cradlevhtapp.utilitiles;

import com.cradletrial.cradlevhtapp.model.Reading;
import com.cradletrial.cradlevhtapp.model.Settings;
import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonDeserializationContext;
import com.google.gson.JsonDeserializer;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParseException;
import com.google.gson.JsonPrimitive;
import com.google.gson.JsonSerializationContext;
import com.google.gson.JsonSerializer;

import org.threeten.bp.ZonedDateTime;
import org.threeten.bp.format.DateTimeFormatter;

import java.lang.reflect.Type;

public class GsonUtil {
    public static <T> T cloneViaGson(T source, Class<T> sourceType) {
        Gson gson = makeGson();
        String json = gson.toJson(source);
        T cloned = gson.fromJson(json, sourceType);
        return cloned;
    }

    public static <T> boolean identicalContentViaGson(T obj1, T obj2) {
        Gson gson = makeGson();
        String str1 = gson.toJson(obj1);
        String str2 = gson.toJson(obj2);
        return str1.equals(str2);
    }

    private static Gson makeGson() {
        return new GsonBuilder()
                .registerTypeAdapter(ZonedDateTime.class, ZDT_DESERIALIZER)
                .registerTypeAdapter(ZonedDateTime.class, ZDT_SERIALIZER)
                .create();
    }

    // Serialize/deserialize ZonedDateTime
    private static final DateTimeFormatter FORMATTER = DateTimeFormatter.ISO_DATE_TIME;
    private static final JsonDeserializer<ZonedDateTime> ZDT_DESERIALIZER = new JsonDeserializer<ZonedDateTime>() {
        @Override
        public ZonedDateTime deserialize(final JsonElement json, final Type typeOfT, final JsonDeserializationContext context) throws JsonParseException {
            return FORMATTER.parse(json.getAsString(), ZonedDateTime::from);
        }
    };
    private static final JsonSerializer<ZonedDateTime> ZDT_SERIALIZER = new JsonSerializer<ZonedDateTime>() {
        @Override
        public JsonElement serialize(ZonedDateTime src, Type typeOfSrc, JsonSerializationContext context) {
            return new JsonPrimitive(FORMATTER.format(src));
        }
    };

    public static <T> String getJson(T obj) {
        Gson gson = makeGson();
        return gson.toJson(obj);
    }

    public static <T> T makeObjectFromJson(String json, Class<T> type) {
        Gson gson = makeGson();
        T cloned = gson.fromJson(json, type);
        return cloned;
    }


    // Add extra fields needed for syncing to server (settings)
    public static <T> String getJsonForSyncingToServer(T obj, Settings settings) {
        Gson gson = makeGson();

        // the reading
        JsonObject jsonObject = gson.toJsonTree(obj).getAsJsonObject();

        // app settings (where needed)
        jsonObject.addProperty("vhtName", settings.getVhtName());
        jsonObject.addProperty("region", settings.getRegion());
        jsonObject.addProperty("ocrEnabled", settings.getOcrEnabled());
        jsonObject.addProperty("uploadImages", settings.shouldUploadImages());

        return jsonObject.toString();
    }

}
