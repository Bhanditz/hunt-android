package com.ctech.eaty.entity

import io.realm.RealmList
import io.realm.RealmModel
import io.realm.annotations.RealmClass
import org.joda.time.format.ISODateTimeFormat

@RealmClass
open class ProductDetailRealm(var id: Int = -1,
                              var commentCount: Int = 0,
                              var name: String = "",
                              var tagline: String = "",
                              var topics: RealmList<Topic> = RealmList(),
                              var makers: RealmList<UserRealm> = RealmList(),
                              var thumbnail: ThumbNailRealm = ThumbNailRealm.EMPTY,
                              var redirectUrl: String = "",
                              var hunter: UserRealm = UserRealm.ANONYMOUS,
                              var voteCount: Int = 0,
                              var comments: RealmList<CommentRealm> = RealmList(),
                              var relatedPosts: RealmList<ProductRealm> = RealmList(),
                              var installLinks: RealmList<InstallLinkRealm> = RealmList(),
                              var currentUser: CurrentUser = CurrentUser(),
                              var media: RealmList<MediaRealm> = RealmList(),
                              var createdAt: String = "") : RealmModel {

    companion object {
        val EMPTY = ProductDetailRealm()
    }

    fun makeProductDetail(): ProductDetail {

        val fmt = ISODateTimeFormat.dateTimeParser()

        return ProductDetail(id, commentCount, name, tagline, topics.toList(),
                makers.map { it.makeUser() }, thumbnail.makeThumbnail(), redirectUrl,
                hunter.makeUser(), voteCount,
                comments.map { it.makeComment() },
                relatedPosts.map { it.makeProduct() },
                installLinks.map { it.makeInstallLink() },
                currentUser,
                media.map { it.makeMedia() },
                fmt.parseDateTime(createdAt))
    }

}