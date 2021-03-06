package com.werb.g_trending.api

import com.werb.g_trending.model.Repository
import com.werb.g_trending.model.Developer
import com.werb.g_trending.model.User
import com.werb.g_trending.utils.RxHelper
import io.reactivex.Observable
import io.reactivex.ObservableOnSubscribe
import io.reactivex.android.schedulers.AndroidSchedulers
import io.reactivex.schedulers.Schedulers
import org.jsoup.Jsoup
import org.jsoup.nodes.Document
import org.jsoup.select.Elements

/** Created by wanbo <werbhelius@gmail.com> on 2017/9/6. */

object TrendingRequest {

    enum class DailyType { TODAY, WEEK, MONTH }

    private val baseUrl = "https://github.com/trending"

    fun repository(lang: String?, daily: DailyType = DailyType.TODAY): Observable<List<Repository>> {
        return RxHelper.getObservable(JSoupProvider.getTrendingService().getTrending(lang, buildDaily(daily)))
                .flatMap { RxHelper.getObservable(requestRepos(it)) }
    }

    fun developer(lang: String?, daily: DailyType = DailyType.TODAY): Observable<List<Developer>> {
        val url = buildUrl("$baseUrl/developers", lang, daily)
        return Observable.create(ObservableOnSubscribe<List<Developer>> {
            val users = requestUsers(url)
            it.onNext(users)
            it.onComplete()
        }).subscribeOn(Schedulers.io()).observeOn(AndroidSchedulers.mainThread())
    }

    private fun buildUrl(url: String, lang: String?, daily: DailyType): String {
        return when (daily) {
            DailyType.TODAY -> "$url/$lang?since=daily"
            DailyType.WEEK -> "$url/$lang?since=weekly"
            DailyType.MONTH -> "$url/$lang?since=monthly"
        }
    }

    private fun buildDaily(daily: DailyType): String {
        return when (daily) {
            DailyType.TODAY -> "daily"
            DailyType.WEEK -> "weekly"
            DailyType.MONTH -> "monthly"
        }
    }


    private fun requestRepos(url: String): Observable<List<Repository>> {
        return Observable.fromPublisher { s ->
            val trendingList = mutableListOf<Repository>()
            val document: Document = Jsoup.parse(url)
            val repoList = document.select(".repo-list")
            if (repoList.isNotEmpty()) {
                val list: Elements? = repoList.select("li")
                list?.let {
                    if (list.isNotEmpty()) {
                        it.onEach {
                            val title = it.select(".d-inline-block > h3 > a").text()
                            val url = it.select(".d-inline-block > h3 > a").attr("href")
                            val description = it.select(".py-1 > p").text()
                            val stars = it.select(".f6 > a[href*=/stargazers]").text()
                            val forks = it.select(".f6 > a[href*=/network]").text()
                            val color = it.select(".f6 .mr-3 > span.repo-language-color").attr("style")
                                    .replace("background-color:", "").replace(";", "")
                            var todayStars = it.select(".f6 > span.float-right").text()
                            if (todayStars.isNullOrBlank()) {
                                todayStars = it.select(".f6 > span.float-sm-right").text()
                            }
                            var language = it.select(".f6 .mr-3 > span[itemprop=programmingLanguage]").text()
                            if (language.isNullOrBlank()) {
                                language = it.select(".f6 span[itemprop=programmingLanguage]").text()
                            }
                            val aTag = it.select(".f6 span a.no-underline")
                            val contributors = aTag.attr("href")
                            val users = arrayListOf<User>()
                            if (aTag.isNotEmpty()) {
                                val images = aTag.select("img")
                                images.onEach {
                                    val name = it.attr("title")
                                    val avatar = it.attr("src")
                                    users.add(User(name, avatar))
                                }
                            }
                            val repos = Repository(title, description, url, stars, forks, color, todayStars, language, contributors, users)
                            trendingList.add(repos)
                        }
                    }
                }
            }
            s.onNext(trendingList)
            s.onComplete()
        }
    }

    private fun requestUsers(url: String): List<Developer> {
        val userList = mutableListOf<Developer>()
        val document: Document = Jsoup.connect(url).get()
        val list = document.select("div.explore-content ol")
        if (list.isNotEmpty()) {
            val li: Elements? = list.select("li")
            li?.let {
                if (li.isNotEmpty()) {
                    it.onEach {
                        val avatar = it.select(".d-flex div.mx-2 a:eq(0) img.rounded-1").attr("src")
                        val name = it.select(".d-flex div.mx-2 h2 a").text()
                        val url = it.select(".d-flex div.mx-2 h2 a").attr("href")
                        val repositoryUrl = it.select(".d-flex div.mx-2 a:eq(1)").attr("href")
                        val repositoryName = it.select(".d-flex div.mx-2 a:eq(1) span.repo-snipit-name span.repo").text()
                        val repositoryDesc = it.select(".d-flex div.mx-2 a:eq(1) span.repo-snipit-description ").text()
                        val user = Developer(name, avatar, url, repositoryUrl, repositoryName, repositoryDesc)
                        userList.add(user)
                    }
                }
            }
        }
        return userList
    }

}