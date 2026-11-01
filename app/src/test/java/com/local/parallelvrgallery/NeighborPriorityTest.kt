package com.local.parallelvrgallerypro

import org.junit.Assert.*
import org.junit.Test

class NeighborPriorityTest {
    @Test fun directionChangeCannotReviveFailedOrUserPausedNeighbors() {
        for(autoPaused in listOf(false,true)) {
            assertFalse(NeighborPriority.canAdmit(VrState.FAILED,autoPaused))
            assertFalse(NeighborPriority.canAdmit(VrState.GENERATING,autoPaused))
            assertFalse(NeighborPriority.canAdmit(VrState.QUEUED,autoPaused))
        }
        assertFalse(NeighborPriority.canAdmit(VrState.PAUSED,false))
        assertTrue(NeighborPriority.canAdmit(VrState.PAUSED,true))
        assertTrue(NeighborPriority.canAdmit(VrState.NORMAL,false))
    }
    private fun order(direction:Int,window:Int=2)=(-window..window).filter{it!=0}.sortedBy{NeighborPriority.rank(it,direction)}
    @Test fun firstOpenStaysBalancedAndCurrentAlwaysWins() {
        assertEquals(listOf(-1,1,-2,2),order(0))
        for(d in -1..1)assertEquals(0,NeighborPriority.rank(0,d))
    }
    @Test fun movementFavorsTwoForwardButRetainsReverseNeighbors() {
        assertEquals(listOf(1,2,-1,-2),order(1))
        assertEquals(listOf(-1,-2,1,2),order(-1))
    }
    @Test fun directionUsesCapturedPositionsAndReversesImmediately() {
        assertEquals(1,NeighborPriority.direction(4,5));assertEquals(-1,NeighborPriority.direction(5,4))
        assertEquals(0,NeighborPriority.direction(-1,4));assertEquals(0,NeighborPriority.direction(4,4))
    }
    @Test fun rankingDoesNotExpandTierAndReverseSideIsNotStarved() {
        for(tier in listOf(2,4,8))for(d in -1..1){val sorted=order(d,tier)
            assertEquals(2*tier,sorted.size);assertEquals(((-tier..tier).toSet()-0),sorted.toSet())
            if(d!=0)assertTrue(sorted.indexOf(-d)<=2)
        }
    }
}
