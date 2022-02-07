package com.virtuallibrary.libraworks

import android.annotation.SuppressLint
import android.content.Context
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.BaseAdapter
import android.widget.ImageView
import android.widget.TextView
import com.squareup.picasso.Picasso


internal class ScrollSearchAdapter(
    private val context: Context,
    private val resultsArray: ArrayList<Book>
) :
    BaseAdapter() {
    private var layoutInflater: LayoutInflater? = null
    private lateinit var imageView: ImageView
    private lateinit var title: TextView
    private lateinit var author: TextView
    override fun getCount(): Int {
        return resultsArray.size
    }

    override fun getItem(position: Int): Any? {
        return null
    }

    override fun getItemId(position: Int): Long {
        return 0
    }

    @SuppressLint("InflateParams")
    override fun getView(
        position: Int,
        convertView: View?,
        parent: ViewGroup
    ): View {
        var convertView = convertView
        if (layoutInflater == null) {
            layoutInflater =
                context.getSystemService(Context.LAYOUT_INFLATER_SERVICE) as LayoutInflater
        }
        if (convertView == null) {
            convertView = layoutInflater!!.inflate(R.layout.activity_rowitem, null)
        }
        imageView = convertView!!.findViewById(R.id.scrollableBookCover)
        title = convertView.findViewById(R.id.scrollableBookAva)
        author = convertView.findViewById((R.id.scrollableBookTotal))

        Picasso.get().load(
            "https://covers.openlibrary.org/b/id/${resultsArray[position].coverId}-L.jpg"
        ).into(imageView)

        title.text = resultsArray[position].title
        author.text = resultsArray[position].author
        return convertView
    }
}