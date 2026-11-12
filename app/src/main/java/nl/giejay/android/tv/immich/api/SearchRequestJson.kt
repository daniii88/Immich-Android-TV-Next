package nl.giejay.android.tv.immich.api

import com.google.gson.Gson
import com.google.gson.GsonBuilder
import com.google.gson.JsonElement
import com.google.gson.JsonNull
import com.google.gson.TypeAdapter
import com.google.gson.TypeAdapterFactory
import com.google.gson.reflect.TypeToken
import com.google.gson.stream.JsonReader
import com.google.gson.stream.JsonWriter
import nl.giejay.android.tv.immich.api.model.SearchRequest

/**
 * The JSON of the API. Null fields are left out as usual ("any"), except the state and country of a
 * [SearchRequest.exactPlace] search: those are sent as null, so a place without a region or country finds only the
 * photos without one, not those of every same-named place.
 */
object SearchRequestJson {
    val gson: Gson = GsonBuilder().registerTypeAdapterFactory(ExactPlaceFactory).create()

    private object ExactPlaceFactory : TypeAdapterFactory {
        override fun <T : Any?> create(gson: Gson, type: TypeToken<T>): TypeAdapter<T>? {
            if (type.rawType != SearchRequest::class.java) return null
            val delegate = gson.getDelegateAdapter(this, TypeToken.get(SearchRequest::class.java))
            val elements = gson.getAdapter(JsonElement::class.java)
            @Suppress("UNCHECKED_CAST")
            return object : TypeAdapter<SearchRequest>() {
                override fun write(out: JsonWriter, value: SearchRequest?) {
                    if (value == null || !value.exactPlace) return delegate.write(out, value)
                    val tree = delegate.toJsonTree(value).asJsonObject
                    // the tree keeps null fields; leave them out as a plain request does
                    tree.keySet().filter { tree.get(it).isJsonNull }.forEach { tree.remove(it) }
                    if (value.state == null) tree.add("state", JsonNull.INSTANCE)
                    if (value.country == null) tree.add("country", JsonNull.INSTANCE)
                    val serializeNulls = out.serializeNulls
                    out.serializeNulls = true
                    try {
                        elements.write(out, tree)
                    } finally {
                        out.serializeNulls = serializeNulls
                    }
                }

                override fun read(reader: JsonReader): SearchRequest? = delegate.read(reader)
            } as TypeAdapter<T>
        }
    }
}
