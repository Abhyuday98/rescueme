package com.myapplication.rescueme

import android.content.Context
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.BaseAdapter
import android.widget.ImageButton
import android.widget.TextView
import android.widget.Toast


class ContactAdapter(private val context: Context, private val dataSource: ArrayList<Contact>) : BaseAdapter() {

    private val inflater: LayoutInflater = context.getSystemService(Context.LAYOUT_INFLATER_SERVICE) as LayoutInflater

    override fun getCount(): Int {
        return dataSource.size
    }

    override fun getItem(position: Int): Any {
        return dataSource[position]
    }

    override fun getItemId(position: Int): Long {
        return position.toLong()
    }

    override fun getView(position: Int, convertView: View?, parent: ViewGroup): View {
        val rowView = inflater.inflate(R.layout.custom_list, parent, false)

        val cName = rowView.findViewById<TextView>(R.id.cName)
        val cNumber = rowView.findViewById<TextView>(R.id.cNumber)

        val contact = getItem(position) as Contact
        cName.text = contact.name
        cNumber.text = contact.number

        val removeImageBtn = rowView.findViewById<ImageButton>(R.id.removeImageBtn)

        removeImageBtn.isFocusable = false
        removeImageBtn.isFocusableInTouchMode = false

        // Figure out how to remove item based on the ImageButton click.
        removeImageBtn.setOnClickListener {
            fun onClick(v: View?) {
                dataSource.removeAt(position)
                this.notifyDataSetChanged()
                Toast.makeText(context, "button has been clicked!", Toast.LENGTH_SHORT).show()
            }
        }

        return rowView
    }

}
