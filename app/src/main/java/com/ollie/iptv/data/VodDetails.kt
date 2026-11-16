package com.ollie.iptv.data
data class VodDetails(
    val title:String,val posterUrl:String="",val backdropUrl:String="",val plot:String="",
    val genre:String="",val releaseDate:String="",val duration:String="",val rating:String="",
    val director:String="",val cast:String="",val seasonCount:Int=0,val episodeCount:Int=0,
    val episodes:List<StreamEntity> = emptyList()
)
