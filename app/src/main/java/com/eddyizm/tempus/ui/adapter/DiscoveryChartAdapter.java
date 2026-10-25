package com.eddyizm.tempus.ui.adapter;

import android.view.*;
import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;
import com.eddyizm.tempus.R;
import com.eddyizm.tempus.databinding.ItemDiscoveryChartBinding;
import com.eddyizm.tempus.discovery.ChartTrack;
import com.eddyizm.tempus.glide.CustomGlideRequest;
import java.util.*;
import java.util.function.Consumer;

public final class DiscoveryChartAdapter extends RecyclerView.Adapter<DiscoveryChartAdapter.Holder> {
    private List<ChartTrack> rows=Collections.emptyList();
    private final Consumer<ChartTrack> click;
    public DiscoveryChartAdapter(Consumer<ChartTrack> click) { this.click=click; }
    public void setItems(List<ChartTrack> items,boolean playableOnly) {
        rows=new ArrayList<>();for (ChartTrack track:items) if (!playableOnly || !track.matches.isEmpty()) rows.add(track);
        notifyDataSetChanged();
    }
    @NonNull @Override public Holder onCreateViewHolder(@NonNull ViewGroup parent,int type) {
        return new Holder(ItemDiscoveryChartBinding.inflate(LayoutInflater.from(parent.getContext()),parent,false));
    }
    @Override public void onBindViewHolder(@NonNull Holder holder,int index) {
        ChartTrack track=rows.get(index);var context=holder.itemView.getContext();
        holder.bind.chartRank.setText(String.valueOf(track.rank));holder.bind.chartTitle.setText(track.title);
        holder.bind.chartArtist.setText(track.artist);
        holder.bind.chartAvailability.setText(track.matchFailed?context.getString(R.string.discovery_match_failed):!track.checked?context.getString(R.string.discovery_not_checked)
                :track.matches.isEmpty()?context.getString(R.string.discovery_not_found)
                :track.matches.size()>1?context.getString(R.string.discovery_versions,track.matches.size())
                :context.getString(R.string.discovery_available));
        CustomGlideRequest.Builder.from(context,track.artwork,CustomGlideRequest.ResourceType.Song).build().into(holder.bind.chartArt);
    }
    @Override public int getItemCount() { return rows.size(); }
    final class Holder extends RecyclerView.ViewHolder {
        final ItemDiscoveryChartBinding bind;
        Holder(ItemDiscoveryChartBinding bind) { super(bind.getRoot());this.bind=bind;itemView.setOnClickListener(v -> {
            int index=getBindingAdapterPosition();if (index!=RecyclerView.NO_POSITION) click.accept(rows.get(index));
        }); }
    }
}
