package de.cyclingsir.cetrack.tour.domain

object TourDedup {
    private const val RATIO = 0.005
    private const val FLOOR_M = 5

    fun distanceRange(distance: Int): Pair<Int, Int> {
        val tol = maxOf(Math.round(distance * RATIO).toInt(), FLOOR_M)
        return (distance - tol) to (distance + tol)
    }
}
