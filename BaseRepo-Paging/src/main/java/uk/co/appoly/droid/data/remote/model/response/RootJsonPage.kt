package uk.co.appoly.droid.data.remote.model.response

interface RootJsonPage<T> : RootJson {
	fun hasData(): Boolean
	fun asPageData(): PageData<T>
}