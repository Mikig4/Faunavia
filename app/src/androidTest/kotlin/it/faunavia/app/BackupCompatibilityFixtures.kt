package it.faunavia.app

import org.json.JSONObject

/** Synthetic old backups must match the old column inventory, not merely claim its schema number. */
internal fun removePersonalCommonNameColumn(payload: JSONObject) {
    val table = payload.getJSONObject("tables").getJSONObject("observations")
    val columns = table.getJSONArray("columns")
    val index = (0 until columns.length()).single { columns.getString(it) == "personalCommonName" }
    columns.remove(index)
    val rows = table.getJSONArray("rows")
    for (row in 0 until rows.length()) rows.getJSONArray(row).remove(index)
}
