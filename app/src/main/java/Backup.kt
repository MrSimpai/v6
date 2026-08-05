package com.example.medtap.data

import android.content.Context
import android.net.Uri
import android.os.PowerManager
import org.json.JSONArray
import org.json.JSONObject

/**
 * Sauvegarde locale, en JSON, dans un fichier que la personne choisit elle-même.
 *
 * Pas de compte, pas de nuage, pas de serveur à maintenir dans cinq ans. Un fichier qu'elle
 * peut mettre dans Drive, s'envoyer par courriel ou oublier — c'est le seul format de
 * sauvegarde qui survit à la disparition de celui qui a écrit l'app.
 *
 * Ça devient nécessaire dès que la série a une valeur affective : perdre deux cents jours
 * en changeant de téléphone serait une vraie peine, pas un désagrément.
 */
object Backup {

    private const val VERSION = 1

    suspend fun export(ctx: Context, uri: Uri) {
        val dao = Db.get(ctx).dao()
        val root = JSONObject()
        root.put("version", VERSION)
        root.put("exportedAt", System.currentTimeMillis())

        root.put("medications", JSONArray().apply {
            dao.activeMedsOnce().forEach { m ->
                put(JSONObject().apply {
                    put("tagId", m.tagId); put("name", m.name); put("doseText", m.doseText)
                    put("hourOfDay", m.hourOfDay); put("minute", m.minute)
                    put("nagEveryMinutes", m.nagEveryMinutes); put("active", m.active)
                })
            }
        })

        root.put("logs", JSONArray().apply {
            dao.allLogs().forEach { l ->
                put(JSONObject().apply {
                    put("tagId", l.tagId); put("scheduledFor", l.scheduledFor)
                    put("takenAt", l.takenAt); put("skipped", l.skipped)
                })
            }
        })

        root.put("cosmetics", JSONArray().apply {
            dao.cosmeticsOnce().forEach { c ->
                put(JSONObject().apply {
                    put("id", c.id); put("unlockedAt", c.unlockedAt); put("equipped", c.equipped)
                })
            }
        })

        root.put("freezes", JSONArray().apply {
            dao.allFreezes().forEach { f ->
                put(JSONObject().apply {
                    put("dayStart", f.dayStart); put("usedAt", f.usedAt)
                })
            }
        })

        ctx.contentResolver.openOutputStream(uri, "wt")?.use {
            it.write(root.toString(2).toByteArray())
        }
    }

    /**
     * Restauration additive : rien n'est effacé, tout est fusionné. Une restauration qui
     * commence par vider la base est une restauration qui, si le fichier est mauvais,
     * détruit ce qu'elle était censée sauver.
     */
    suspend fun import(ctx: Context, uri: Uri): Int {
        val text = ctx.contentResolver.openInputStream(uri)?.use {
            it.readBytes().toString(Charsets.UTF_8)
        } ?: return 0
        val root = JSONObject(text)
        val dao = Db.get(ctx).dao()
        var n = 0

        root.optJSONArray("medications")?.let { arr ->
            for (i in 0 until arr.length()) {
                val o = arr.getJSONObject(i)
                dao.upsert(
                    Medication(
                        tagId = o.getString("tagId"), name = o.getString("name"),
                        doseText = o.getString("doseText"),
                        hourOfDay = o.getInt("hourOfDay"), minute = o.getInt("minute"),
                        nagEveryMinutes = o.optInt("nagEveryMinutes", 10),
                        active = o.optBoolean("active", true)
                    )
                )
                n++
            }
        }
        root.optJSONArray("logs")?.let { arr ->
            for (i in 0 until arr.length()) {
                val o = arr.getJSONObject(i)
                dao.insert(
                    DoseLog(
                        tagId = o.getString("tagId"),
                        scheduledFor = o.getLong("scheduledFor"),
                        takenAt = o.getLong("takenAt"),
                        skipped = o.optBoolean("skipped", false)
                    )
                )
                n++
            }
        }
        root.optJSONArray("cosmetics")?.let { arr ->
            for (i in 0 until arr.length()) {
                val o = arr.getJSONObject(i)
                dao.grant(
                    OwnedCosmetic(
                        o.getString("id"), o.getLong("unlockedAt"),
                        o.optBoolean("equipped", true)
                    )
                )
            }
        }
        root.optJSONArray("freezes")?.let { arr ->
            for (i in 0 until arr.length()) {
                val o = arr.getJSONObject(i)
                dao.insertFreeze(StreakFreeze(o.getLong("dayStart"), o.getLong("usedAt")))
            }
        }
        return n
    }

    fun suggestedFileName(): String {
        val d = java.text.SimpleDateFormat("yyyy-MM-dd", java.util.Locale.CANADA_FRENCH)
        return "framboise-${d.format(java.util.Date())}.json"
    }
}

/**
 * Est-ce que les rappels ont une chance de partir ?
 *
 * Sur Samsung — et le S25 ne fait pas exception — One UI met en veille les applications
 * peu utilisées et coupe leurs alarmes, sans rien dire. Le symptôme est le pire possible
 * pour une app de médication : plus aucune notification, et personne ne s'en aperçoit.
 * Une app qui échoue en silence est pire que pas d'app, parce qu'on a arrêté de vérifier
 * soi-même.
 */
object ReminderHealth {

    fun batteryUnrestricted(ctx: Context): Boolean =
        ctx.getSystemService(PowerManager::class.java)
            ?.isIgnoringBatteryOptimizations(ctx.packageName) ?: true

    /** Le texte des réglages Samsung, écrit pour être suivi sans réfléchir. */
    const val SAMSUNG_STEPS =
        "Sur Samsung, ajoute aussi l'app aux exceptions : Paramètres → Batterie → " +
            "Limites d'utilisation en arrière-plan → Applications jamais mises en veille → +"
}
