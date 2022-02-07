package com.virtuallibrary.libraworks

class Book {
    var isbn: String = ""
    var subtitle: String = ""
    var title: String = ""
    var fullTitle: String = ""
    var pubDate: String = ""
    var coverId: String = ""
    var author: String = ""

    // Only needed for checkout log
    var libraryName: String = ""
    var libraryId: String = ""
    var checkoutDate: String = ""
}