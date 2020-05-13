package com.espressif.provisioningap;

import android.app.Activity;
import android.content.Context;
import android.content.Intent;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import java.util.ArrayList;

public class DeviceListAdapter extends RecyclerView.Adapter<DeviceListAdapter.MyViewHolder> {

    private Context context;
    private ArrayList<String> devices;

    public DeviceListAdapter(Context context, ArrayList<String> devices) {

        this.context = context;
        this.devices = devices;
    }

    @Override
    public MyViewHolder onCreateViewHolder(ViewGroup parent, int viewType) {

        // infalte the item Layout
        LayoutInflater layoutInflater = LayoutInflater.from(context);
        View v = layoutInflater.inflate(R.layout.item_device, parent, false);
        // set the view's size, margins, paddings and layout parameters
        MyViewHolder vh = new MyViewHolder(v); // pass the view to View Holder
        return vh;
    }

    @Override
    public void onBindViewHolder(@NonNull final MyViewHolder myViewHolder, final int position) {

        myViewHolder.tvDevice.setText(devices.get(position));

        if (devices.get(position).equals("BLE")) {
            myViewHolder.ivDevice.setImageResource(R.drawable.ic_bt);
        } else {
            myViewHolder.ivDevice.setImageResource(R.drawable.ic_wifi);
        }

        // implement setOnClickListener event on item view.
        myViewHolder.itemView.setOnClickListener(new View.OnClickListener() {

            @Override
            public void onClick(View view) {
                // TODO
                Toast.makeText(context, "" + devices.get(position) + " clicked", Toast.LENGTH_LONG).show();
            }
        });
    }

    @Override
    public int getItemCount() {
        return devices.size();
    }

    public class MyViewHolder extends RecyclerView.ViewHolder {

        // init the item view's
        TextView tvDevice;
        ImageView ivDevice;

        public MyViewHolder(View itemView) {
            super(itemView);

            // get the reference of item view's
            ivDevice = itemView.findViewById(R.id.iv_device);
            tvDevice = itemView.findViewById(R.id.tv_device);
        }
    }
}
