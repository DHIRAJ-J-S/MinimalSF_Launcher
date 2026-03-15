package com.minimal.launcher

import android.content.Context
import org.json.JSONArray
import org.json.JSONObject

data class TodoItem(val id: Long, val text: String, val done: Boolean, val important: Boolean = false)

object TodoStore {
    private const val PREF = "todo_prefs"
    private const val KEY = "todos"

    fun load(ctx: Context): MutableList<TodoItem> {
        val prefs = ctx.getSharedPreferences(PREF, Context.MODE_PRIVATE)
        val json = prefs.getString(KEY, "[]") ?: "[]"
        val arr = JSONArray(json)
        val list = mutableListOf<TodoItem>()
        for (i in 0 until arr.length()) {
            val obj = arr.getJSONObject(i)
            list.add(TodoItem(
                obj.getLong("id"),
                obj.getString("text"),
                obj.getBoolean("done"),
                obj.optBoolean("important", false)
            ))
        }
        return list
    }

    fun save(ctx: Context, items: List<TodoItem>) {
        val arr = JSONArray()
        items.forEach { item ->
            arr.put(JSONObject().apply {
                put("id", item.id)
                put("text", item.text)
                put("done", item.done)
                put("important", item.important)
            })
        }
        ctx.getSharedPreferences(PREF, Context.MODE_PRIVATE)
            .edit().putString(KEY, arr.toString()).apply()
    }
}
