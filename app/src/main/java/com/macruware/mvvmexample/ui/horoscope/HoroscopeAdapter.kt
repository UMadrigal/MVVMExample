package com.macruware.mvvmexample.ui.horoscope

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.view.animation.LinearInterpolator
import androidx.recyclerview.widget.RecyclerView
import com.macruware.mvvmexample.R
import com.macruware.mvvmexample.databinding.ItemHoroscopeBinding
import com.macruware.mvvmexample.domain.model.HoroscopeInfo
import android.animation.TimeInterpolator
import android.view.animation.*
import androidx.interpolator.view.animation.FastOutLinearInInterpolator
import androidx.interpolator.view.animation.LinearOutSlowInInterpolator

class HoroscopeAdapter(
    private var horoscopeList: List<HoroscopeInfo> = emptyList(),
    private val onItemSelected: (horoscopeSelected: HoroscopeInfo) -> Unit
) :
    RecyclerView.Adapter<HoroscopeViewHolder>() {

    override fun onCreateViewHolder(
        parent: ViewGroup,
        viewType: Int
    ): HoroscopeViewHolder {
        return HoroscopeViewHolder(
            LayoutInflater.from(parent.context).inflate(R.layout.item_horoscope, parent, false)
        )
    }

    override fun onBindViewHolder(
        holder: HoroscopeViewHolder,
        position: Int
    ) {
        holder.bind(horoscopeList[position], onItemSelected, position)
    }

    override fun getItemCount() = horoscopeList.size

    // pendiente de actualizar a DiffUtil
    fun updateList(list: List<HoroscopeInfo>) {
        horoscopeList = list
        notifyDataSetChanged()
    }
}


class HoroscopeViewHolder(view: View) : RecyclerView.ViewHolder(view) {

    private val binding = ItemHoroscopeBinding.bind(view)
    private val context = binding.root.context

    fun bind(
        horoscopeInfo: HoroscopeInfo,
        onItemSelected: (horoscopeSelected: HoroscopeInfo) -> Unit,
        position1: Int
    ) = with(binding) {
        tvTitle.text = context.getString(horoscopeInfo.name)
        ivHoroscope.setImageResource(horoscopeInfo.img)

        parent.setOnClickListener {
            val position = position1
            if (position != RecyclerView.NO_POSITION) {
                startRotationAnimation(
                    ivHoroscope,
                    interpolators[position % interpolators.size],
                    clickAction = { onItemSelected(horoscopeInfo) })
            }
        }
    }

    private fun startRotationAnimation(
        view: View,
        timeInterpolator: TimeInterpolator,
        clickAction: () -> Unit
    ) {
        view.animate().apply {
            duration = 1000
            interpolator = timeInterpolator
            rotationBy(360f)
            withEndAction { clickAction() }
            start()
        }
    }

    companion object {
        // Uno por cada uno de los 12 ítems, según su posición
        private val interpolators: List<TimeInterpolator> = listOf(
            LinearInterpolator(),
            AccelerateInterpolator(),
            DecelerateInterpolator(),
            AccelerateDecelerateInterpolator(),
            AnticipateInterpolator(),
            OvershootInterpolator(),
            AnticipateOvershootInterpolator(),
            BounceInterpolator(),
            CycleInterpolator(1f),
            PathInterpolator(0.68f, -0.6f, 0.32f, 1.6f), // curva Bézier propia
            FastOutLinearInInterpolator(),
            LinearOutSlowInInterpolator()
        )
    }
}