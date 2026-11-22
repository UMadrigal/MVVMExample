package com.macruware.mvvmexample.data.providers

import com.macruware.mvvmexample.domain.model.HoroscopeInfo
import com.macruware.mvvmexample.domain.model.HoroscopeInfo.*
import javax.inject.Inject

// Provider local, lista creada a partir de datos fijos
class HoroscopeProvider @Inject constructor() {
    fun getHoroscopes(): List<HoroscopeInfo>{
        return listOf(
            Aries,
            Taurus,
            Gemini,
            Cancer,
            Leo,
            Virgo,
            Libra,
            Scorpio,
            Sagittarius,
            Capricorn,
            Aquarius,
            Pisces)
    }
}