package com.scanner.pro.ui.filters

import android.graphics.Bitmap
import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.recyclerview.widget.RecyclerView
import com.scanner.pro.R
import com.scanner.pro.model.ScanFilterType

class FilterAdapter(
    private val filters: List<ScanFilterType>,
    private val onSelected: (ScanFilterType) -> Unit
) : RecyclerView.Adapter<FilterAdapter.VH>() {

    private val thumbnails = mutableMapOf<ScanFilterType, Bitmap>()
    private var selected: ScanFilterType = ScanFilterType.DOCUMENT

    class VH(view: android.view.View) : RecyclerView.ViewHolder(view) {
        val frame: android.widget.FrameLayout = view.findViewById(R.id.thumb_frame)
        val thumb: android.widget.ImageView = view.findViewById(R.id.thumb)
        val label: android.widget.TextView = view.findViewById(R.id.label)
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): VH {
        val view = LayoutInflater.from(parent.context).inflate(R.layout.item_filter_thumb, parent, false)
        return VH(view)
    }

    override fun onBindViewHolder(holder: VH, position: Int) {
        val filter = filters[position]
        holder.label.text = displayName(filter)
        thumbnails[filter]?.let { holder.thumb.setImageBitmap(it) }
        holder.frame.isSelected = filter == selected
        holder.itemView.setOnClickListener {
            selected = filter
            onSelected(filter)
            notifyDataSetChanged()
        }
    }

    override fun getItemCount() = filters.size

    fun setThumbnails(map: Map<ScanFilterType, Bitmap>) {
        thumbnails.clear()
        thumbnails.putAll(map)
        notifyDataSetChanged()
    }

    fun setSelected(filter: ScanFilterType) {
        selected = filter
        notifyDataSetChanged()
    }

    companion object {
        /** Order + naming matches the familiar CamScanner-style filter strip. */
        val DISPLAY_ORDER = listOf(
            ScanFilterType.ORIGINAL,
            ScanFilterType.LIGHTEN,
            ScanFilterType.AUTO_ENHANCE,
            ScanFilterType.MAGIC_COLOR,
            ScanFilterType.NO_WATERMARK,
            ScanFilterType.NO_SHADOW,
            ScanFilterType.DOCUMENT,
            ScanFilterType.BLACK_AND_WHITE,
            ScanFilterType.GRAYSCALE,
            ScanFilterType.HIGH_CONTRAST,
            ScanFilterType.SHARPEN,
            ScanFilterType.DARKEN,
            ScanFilterType.WARM,
            ScanFilterType.COOL,
            ScanFilterType.SOFT,
            ScanFilterType.BACKGROUND_REMOVAL
        )

        fun displayName(filter: ScanFilterType): String = when (filter) {
            ScanFilterType.ORIGINAL -> "Original"
            ScanFilterType.LIGHTEN -> "Lighten"
            ScanFilterType.AUTO_ENHANCE -> "Enhance"
            ScanFilterType.MAGIC_COLOR -> "Magic Pro"
            ScanFilterType.NO_WATERMARK -> "No Watermark"
            ScanFilterType.NO_SHADOW -> "No Shadow"
            ScanFilterType.DOCUMENT -> "Document"
            ScanFilterType.BLACK_AND_WHITE -> "B&W"
            ScanFilterType.GRAYSCALE -> "Grayscale"
            ScanFilterType.HIGH_CONTRAST -> "Contrast"
            ScanFilterType.SHARPEN -> "Sharpen"
            ScanFilterType.DARKEN -> "Darken"
            ScanFilterType.WARM -> "Warm"
            ScanFilterType.COOL -> "Cool"
            ScanFilterType.SOFT -> "Soft"
            ScanFilterType.BACKGROUND_REMOVAL -> "No Background"
        }
    }
}
