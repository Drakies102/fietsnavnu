package com.fietsrouten.ui.rides

import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.recyclerview.widget.RecyclerView
import com.fietsrouten.R
import com.fietsrouten.data.model.RideRecord
import com.fietsrouten.databinding.ItemRideBinding
import java.text.DateFormat
import java.text.NumberFormat

class RidesAdapter(private val rides: List<RideRecord>) : RecyclerView.Adapter<RidesAdapter.ViewHolder>() {

    class ViewHolder(val binding: ItemRideBinding) : RecyclerView.ViewHolder(binding.root)

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        val binding = ItemRideBinding.inflate(LayoutInflater.from(parent.context), parent, false)
        return ViewHolder(binding)
    }

    override fun getItemCount() = rides.size

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        val ride = rides[position]
        val context = holder.itemView.context
        val locale = context.resources.configuration.locales[0]

        val dateStr = DateFormat.getDateInstance(DateFormat.MEDIUM, locale).format(ride.timestampMs)
        holder.binding.tvRideTitle.text = context.getString(R.string.ride_title_format, dateStr)

        val distNf = NumberFormat.getInstance(locale).apply {
            minimumFractionDigits = 1
            maximumFractionDigits = 1
        }
        val distKm = "${distNf.format(ride.distanceMeters / 1000)} ${context.getString(R.string.unit_km_short)}"

        val totalMinutes = (ride.durationMs / 60000).toInt()
        val hours = totalMinutes / 60
        val minutes = totalMinutes % 60
        val hourUnit = context.getString(R.string.unit_hour_short)
        val minUnit = context.getString(R.string.unit_minute_short)
        val time = if (hours > 0) "${hours}$hourUnit ${minutes}$minUnit" else "$minutes $minUnit"

        val meta = if (ride.elevationGainMeters >= 5.0) {
            "$distKm · ↗ ${ride.elevationGainMeters.toInt()} ${context.getString(R.string.unit_meter_short)} · $time"
        } else {
            "$distKm · $time"
        }
        holder.binding.tvRideMeta.text = meta
    }
}
