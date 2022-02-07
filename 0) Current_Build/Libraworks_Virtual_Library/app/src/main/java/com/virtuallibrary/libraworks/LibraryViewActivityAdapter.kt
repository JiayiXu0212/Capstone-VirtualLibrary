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


internal class LibraryViewActivityAdapter(
    private val context: Context,
    private val availBookArray: ArrayList<String>,
    private val numberImage: ArrayList<String>,
    private val bookTitleArray: ArrayList<String>
) :
    BaseAdapter() {
    private var layoutInflater: LayoutInflater? = null
    private lateinit var imageView: ImageView
    private lateinit var availableBooks: TextView
    private lateinit var bookTitle: TextView

    override fun getCount(): Int {
        return availBookArray.size
    }

    override fun getItem(position: Int): Any? {
        return null
    }

    override fun getItemId(position: Int): Long {
        return 0
    }

    @SuppressLint("SetTextI18n", "InflateParams")
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
        availableBooks = convertView.findViewById(R.id.scrollableBookTotal)
        bookTitle = convertView.findViewById(R.id.scrollableBookAva)
        availableBooks.invalidate()
        Picasso.get().load("https://covers.openlibrary.org/b/id/${numberImage[position]}-L.jpg")
            .into(imageView)
        availableBooks.text = "Available Copies: " + availBookArray[position]
        bookTitle.text = bookTitleArray[position]
        return convertView
    }
}