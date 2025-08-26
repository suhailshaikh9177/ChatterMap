package com.example.locatorchat.ui

import androidx.fragment.app.Fragment
import androidx.fragment.app.FragmentActivity
import androidx.viewpager2.adapter.FragmentStateAdapter
import com.example.locatorchat.ui.fragments.ChatsFragment
import com.example.locatorchat.ui.fragments.NearbyFragment

class ViewPagerAdapter(fragmentActivity: FragmentActivity) : FragmentStateAdapter(fragmentActivity) {

    override fun getItemCount(): Int = 2

    override fun createFragment(position: Int): Fragment {
        return when (position) {
            0 -> ChatsFragment()
            1 -> NearbyFragment()
            else -> throw IllegalStateException("Invalid position: $position")
        }
    }
}