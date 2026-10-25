package com.eddyizm.tempus.ui.adapter;

import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.ViewGroup;
import android.widget.Filter;
import android.widget.Filterable;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import com.eddyizm.tempus.databinding.ItemHomeCataloguePodcastChannelBinding;
import com.eddyizm.tempus.glide.CustomGlideRequest;
import com.eddyizm.tempus.interfaces.ClickCallback;
import com.eddyizm.tempus.subsonic.models.PodcastChannel;
import com.eddyizm.tempus.util.Constants;
import com.eddyizm.tempus.util.MusicUtil;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

public class PodcastChannelCatalogueAdapter extends RecyclerView.Adapter<PodcastChannelCatalogueAdapter.ViewHolder> implements Filterable, StandardViewTypeAdapter {
    private final ClickCallback click;
    private volatile String category = "";
    private String query = "";

    public void setCategory(String value) { category=value; filtering.filter(query); }
    private final Filter filtering = new Filter() {
        @Override
        protected FilterResults performFiltering(CharSequence constraint) {
            List<PodcastChannel> filteredList = new ArrayList<>();

            if (constraint == null || constraint.length() == 0) {
                for (PodcastChannel item : podcastChannelsFull) if (category.isEmpty() || category.equals(item.getCategory())) filteredList.add(item);
            } else {
                String filterPattern = constraint.toString().toLowerCase().trim();

                for (PodcastChannel item : podcastChannelsFull) {
                    if ((category.isEmpty() || category.equals(item.getCategory())) && (item.getTitle()+" "+item.getCategory()+" "+item.getRegion()).toLowerCase(java.util.Locale.ROOT).contains(filterPattern)) {
                        filteredList.add(item);
                    }
                }
            }

            FilterResults results = new FilterResults();
            results.values = filteredList;
            results.count = filteredList.size();

            return results;
        }

        @Override
        protected void publishResults(CharSequence constraint, FilterResults results) {
            query=constraint == null ? "" : constraint.toString();
            podcastChannels.clear();
            if (results.count > 0) podcastChannels.addAll((List) results.values);
            notifyDataSetChanged();
        }
    };

    private List<PodcastChannel> podcastChannels;
    private volatile List<PodcastChannel> podcastChannelsFull;

    public PodcastChannelCatalogueAdapter(ClickCallback click) {
        this.click = click;
        this.podcastChannels = new ArrayList<>();
        this.podcastChannelsFull = new ArrayList<>();
    }

    @NonNull
    @Override
    public ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        ItemHomeCataloguePodcastChannelBinding view = ItemHomeCataloguePodcastChannelBinding.inflate(LayoutInflater.from(parent.getContext()), parent, false);
        return new ViewHolder(view);
    }

    @Override
    public void onBindViewHolder(ViewHolder holder, int position) {
        PodcastChannel podcastChannel = podcastChannels.get(position);

        holder.item.podcastChannelTitleLabel.setText(podcastChannel.getTitle());
        holder.item.podcastChannelCategoryLabel.setText(podcastChannel.getCategory() == null ? "" : podcastChannel.getCategory()+" · "+podcastChannel.getRegion());

        CustomGlideRequest.Builder
                .from(holder.itemView.getContext(), podcastChannel.getCoverArtId(), CustomGlideRequest.ResourceType.Podcast)
                .build()
                .into(holder.item.podcastChannelCatalogueCoverImageView);
    }

    @Override
    public int getItemCount() {
        return podcastChannels.size();
    }

    public PodcastChannel getItem(int position) {
        return podcastChannels.get(position);
    }

    public void setItems(List<PodcastChannel> podcastChannels) {
        this.podcastChannels = new ArrayList<>(podcastChannels);
        this.podcastChannelsFull = new ArrayList<>(podcastChannels);
        filtering.filter(query);
    }

    @Override
    public long getItemId(int position) {
        return position;
    }

    @Override
    public Filter getFilter() {
        return filtering;
    }

    public class ViewHolder extends RecyclerView.ViewHolder {
        ItemHomeCataloguePodcastChannelBinding item;

        ViewHolder(ItemHomeCataloguePodcastChannelBinding item) {
            super(item.getRoot());

            this.item = item;

            item.podcastChannelTitleLabel.setSelected(true);

            itemView.setOnClickListener(v -> onClick());
            itemView.setOnLongClickListener(v -> onLongClick());
        }

        private void onClick() {
            Bundle bundle = new Bundle();
            bundle.putParcelable(Constants.PODCAST_CHANNEL_OBJECT, podcastChannels.get(getBindingAdapterPosition()));

            click.onPodcastChannelClick(bundle);
        }

        private boolean onLongClick() {
            Bundle bundle = new Bundle();
            bundle.putParcelable(Constants.PODCAST_CHANNEL_OBJECT, podcastChannels.get(getBindingAdapterPosition()));

            click.onPodcastChannelLongClick(bundle);

            return true;
        }
    }
}
