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


internal class CheckOutLogDisplayAdapter(
    private val context: Context,
    private val titleArray: ArrayList<String>,
    private val libraryNameArray: ArrayList<String>,
    private val checkoutDateArray: ArrayList<String>,
    private val coverImageArray: ArrayList<String>
) :
    BaseAdapter() {
    private var layoutInflater: LayoutInflater? = null
    private lateinit var imageView: ImageView
    private lateinit var bookTitle: TextView
    private lateinit var checkoutDate: TextView
    private lateinit var libraryName: TextView

    override fun getCount(): Int {
        return titleArray.size
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
        bookTitle = convertView.findViewById(R.id.scrollableBookTotal)
        libraryName = convertView.findViewById(R.id.scrollableBookAva)
        checkoutDate = convertView.findViewById(R.id.scrollableBookDate)

        bookTitle.invalidate()

        bookTitle.text = titleArray[position]
        libraryName.text = libraryNameArray[position]
        checkoutDate.text = checkoutDateArray[position]

        Picasso.get().load("https://covers.openlibrary.org/b/id/${coverImageArray[position]}-L.jpg")
            .into(imageView)

        return convertView
    }
}