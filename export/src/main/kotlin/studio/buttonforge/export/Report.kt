package studio.buttonforge.export

enum class Fidelity { Kept, Approximated, Dropped }

/** One thing that did not survive the export unchanged. */
data class ReportItem(
    val target: String,
    val layer: String,
    val feature: String,
    val fidelity: Fidelity,
    val note: String = "",
)

/**
 * What the export keeps, approximates or drops, per target. Shown to the user BEFORE anything is written.
 * [keptCount] counts the drawing items that were carried over without any loss.
 */
class ExportReport(val items: List<ReportItem>, val keptCount: Int) {
    val dropped: List<ReportItem> get() = items.filter { it.fidelity == Fidelity.Dropped }
    val approximated: List<ReportItem> get() = items.filter { it.fidelity == Fidelity.Approximated }
    val hasProblems: Boolean get() = items.isNotEmpty()

    fun lines(): List<String> {
        val out = ArrayList<String>()
        out += "عدد عناصر الرسم: $keptCount"
        if (items.isEmpty()) out += "لا يوجد ما يُفقد أو يُقرّب."
        for (i in items) {
            val tag = when (i.fidelity) {
                Fidelity.Dropped -> "يُحذف"
                Fidelity.Approximated -> "يُقرَّب"
                Fidelity.Kept -> "محفوظ"
            }
            out += "[${i.target}] $tag: ${i.feature} (${i.layer})" + if (i.note.isEmpty()) "" else " - ${i.note}"
        }
        return out
    }
}

/** Collects report items without repeating the same one. */
internal class ReportBuilder {
    private val items = LinkedHashSet<ReportItem>()
    var kept = 0

    fun add(item: ReportItem) {
        items += item
    }

    fun build() = ExportReport(items.toList(), kept)
}
