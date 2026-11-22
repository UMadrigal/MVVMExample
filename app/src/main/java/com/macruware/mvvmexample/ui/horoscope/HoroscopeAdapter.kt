package com.macruware.mvvmexample.ui.horoscope

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.recyclerview.widget.RecyclerView
import com.macruware.mvvmexample.R
import com.macruware.mvvmexample.databinding.ItemHoroscopeBinding
import com.macruware.mvvmexample.domain.model.HoroscopeInfo

class HoroscopeAdapter (private var horoscopeList: List<HoroscopeInfo> = emptyList(),
    private val onItemSelected: (horoscopeSelected: HoroscopeInfo) -> Unit):
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
        holder.bind(horoscopeList[position], onItemSelected)
    }

    override fun getItemCount() = horoscopeList.size

    // pendiente de actualizar a DiffUtil
    fun updateList(list: List<HoroscopeInfo>){
        horoscopeList = list
        notifyDataSetChanged()
    }
}

class HoroscopeViewHolder(view: View): RecyclerView.ViewHolder(view){

    private val binding = ItemHoroscopeBinding.bind(view)
    private val context = binding.root.context
    fun bind(
        horoscopeInfo: HoroscopeInfo,
        onItemSelected: (horoscopeSelected: HoroscopeInfo) -> Unit
    ) = with(binding) {
        tvTitle.text = context.getString(horoscopeInfo.name)
        ivHoroscope.setImageResource(horoscopeInfo.img)

        parent.setOnClickListener { onItemSelected(horoscopeInfo) }
    }
}