package com.eddyizm.tempus.ui.adapter;

import android.content.Context;
import android.graphics.Outline;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.view.ViewOutlineProvider;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import com.eddyizm.tempus.R;
import com.eddyizm.tempus.databinding.ItemHomePodcastEpisodeBinding;
import com.eddyizm.tempus.glide.CustomGlideRequest;
import com.eddyizm.tempus.interfaces.ClickCallback;
import com.eddyizm.tempus.subsonic.models.PodcastEpisode;
import com.eddyizm.tempus.util.Constants;
import com.eddyizm.tempus.util.MusicUtil;
import com.eddyizm.tempus.util.Preferences;
import com.google.android.material.color.MaterialColors;

import java.text.SimpleDateFormat;
import java.util.Collections;
import java.util.Date;
import java.util.List;
import java.util.Locale;
import java.util.Objects;
import java.util.stream.Collectors;

public class PodcastEpisodeAdapter extends RecyclerView.Adapter<PodcastEpisodeAdapter.ViewHolder> {
    private final ClickCallback click;

    private List<PodcastEpisode> podcastEpisodes;
    private List<PodcastEpisode> podcastEpisodesFull;

    public PodcastEpisodeAdapter(ClickCallback click) {
        this.click = click;
        this.podcastEpisodes = Collections.emptyList();
        this.podcastEpisodesFull = Collections.emptyList();
    }

    @NonNull
    @Override
    public ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        ItemHomePodcastEpisodeBinding view = ItemHomePodcastEpisodeBinding.inflate(LayoutInflater.from(parent.getContext()), parent, false);
        return new ViewHolder(view);
    }

    @Override
    public void onBindViewHolder(ViewHolder holder, int position) {
        PodcastEpisode podcastEpisode = podcastEpisodes.get(position);
        SimpleDateFormat simpleDateFormat = new SimpleDateFormat("MMM d", Locale.getDefault());

        Context context = holder.itemView.getContext();

        Date publishedDate = podcastEpisode.getPublishDate();
        String dateStr = (publishedDate != null)
                ? simpleDateFormat.format(publishedDate)
                : context.getString(R.string.podcast_release_no_date);

        Integer duration = podcastEpisode.getDuration();
        String durationStr = (duration != null)
                ? MusicUtil.getReadablePodcastDurationString(duration)
                : context.getString(R.string.podcast_release_unknown_duration);

        long savedMs = podcastEpisode.getId() != null ? Preferences.getResumePoint(podcastEpisode.getId()) : 0L;
        long durationMs = (duration != null && duration > 0) ? duration * 1000L : 0L;
        boolean hasResume = savedMs > 0 && (durationMs <= 0 || savedMs < durationMs - 30_000L);

        String releasesAndDurationLabel;
        if (hasResume) {
            String resumeText;
            if (durationMs > savedMs) {
                resumeText = context.getString(
                        R.string.editorial_podcast_resume_remaining,
                        formatClock((durationMs - savedMs) / 1000L)
                );
            } else {
                resumeText = context.getString(
                        R.string.editorial_podcast_resume_at,
                        formatClock(savedMs / 1000L)
                );
            }
            releasesAndDurationLabel = dateStr + " · " + resumeText;
            int permille = durationMs > 0
                    ? (int) Math.max(20L, Math.min(990L, (savedMs * 1000L) / durationMs))
                    : 330;
            holder.item.podcastResumeProgress.setVisibility(View.VISIBLE);
            holder.item.podcastResumeProgress.setProgressCompat(permille, false);
            holder.item.podcastReleasesAndDurationLabel.setTextColor(
                    MaterialColors.getColor(holder.itemView, com.google.android.material.R.attr.colorPrimary)
            );
        } else {
            releasesAndDurationLabel = context.getString(
                    R.string.podcast_release_date_duration_formatter,
                    dateStr,
                    durationStr
            );
            holder.item.podcastResumeProgress.setVisibility(View.GONE);
            holder.item.podcastReleasesAndDurationLabel.setTextColor(
                    MaterialColors.getColor(holder.itemView, com.google.android.material.R.attr.colorOnSurfaceVariant)
            );
        }

        holder.item.podcastTitleLabel.setText(podcastEpisode.getTitle());
        holder.item.podcastSubtitleLabel.setText(podcastEpisode.getArtist());
        holder.item.podcastReleasesAndDurationLabel.setText(releasesAndDurationLabel);

        String desc = MusicUtil.getReadableString(podcastEpisode.getDescription());
        holder.item.podcastDescriptionText.setText(desc);
        boolean hasDesc = desc != null && !desc.trim().isEmpty();
        holder.item.podcastDescriptionText.setVisibility(hasDesc ? View.VISIBLE : View.GONE);
        holder.item.podcastUpperDivider.setVisibility(hasDesc ? View.VISIBLE : View.GONE);

        CustomGlideRequest.Builder
                .from(holder.itemView.getContext(), podcastEpisode.getCoverArtId(), CustomGlideRequest.ResourceType.Podcast)
                .build()
                .into(holder.item.podcastCoverImageView);

        holder.item.podcastPlayButton.setEnabled("completed".equals(podcastEpisode.getStatus()));
        holder.item.podcastMoreButton.setVisibility("completed".equals(podcastEpisode.getStatus()) ? View.VISIBLE : View.GONE);
        holder.item.podcastDownloadRequestButton.setVisibility("completed".equals(podcastEpisode.getStatus()) ? View.GONE : View.VISIBLE);
    }

    private static String formatClock(long totalSeconds) {
        long clamped = Math.max(0L, totalSeconds);
        long hours = clamped / 3600L;
        long minutes = (clamped % 3600L) / 60L;
        long seconds = clamped % 60L;
        if (hours > 0L) {
            return String.format(Locale.US, "%d:%02d:%02d", hours, minutes, seconds);
        }
        return String.format(Locale.US, "%d:%02d", minutes, seconds);
    }

    @Override
    public int getItemCount() {
        return podcastEpisodes != null ? podcastEpisodes.size() : 0;
    }

    public void setItems(List<PodcastEpisode> podcastEpisodes) {
        if (podcastEpisodes == null) {
            this.podcastEpisodesFull = Collections.emptyList();
            this.podcastEpisodes = Collections.emptyList();
        } else {
            this.podcastEpisodesFull = podcastEpisodes.stream()
                    .filter(Objects::nonNull)
                    .collect(Collectors.toList());
            this.podcastEpisodes = podcastEpisodesFull.stream()
                    .filter(podcastEpisode -> Objects.equals(podcastEpisode.getStatus(), "completed"))
                    .collect(Collectors.toList());
        }
        notifyDataSetChanged();
    }

    @Override
    public int getItemViewType(int position) {
        return 0;
    }

    @Override
    public long getItemId(int position) {
        return position;
    }

    public class ViewHolder extends RecyclerView.ViewHolder {
        ItemHomePodcastEpisodeBinding item;

        ViewHolder(ItemHomePodcastEpisodeBinding item) {
            super(item.getRoot());

            this.item = item;

            float radiusPx = 10f * item.getRoot().getResources().getDisplayMetrics().density;
            item.podcastCoverImageView.setOutlineProvider(new ViewOutlineProvider() {
                @Override
                public void getOutline(View view, Outline outline) {
                    outline.setRoundRect(0, 0, view.getWidth(), view.getHeight(), radiusPx);
                }
            });
            item.podcastCoverImageView.setClipToOutline(true);

            itemView.setOnClickListener(v -> onClick());
            itemView.setOnLongClickListener(v -> openMore());

            item.podcastPlayButton.setOnClickListener(v -> onClick());
            item.podcastMoreButton.setOnClickListener(v -> openMore());
            item.podcastDownloadRequestButton.setOnClickListener(v -> requestDownload());
        }

        public void onClick() {
            int position = getBindingAdapterPosition();
            if (position == RecyclerView.NO_POSITION) return;
            PodcastEpisode podcastEpisode = podcastEpisodes.get(position);

            if ("completed".equals(podcastEpisode.getStatus())) {
                Bundle bundle = new Bundle();
                bundle.putParcelable(Constants.PODCAST_OBJECT, podcastEpisodes.get(position));

                click.onPodcastEpisodeClick(bundle);
            }
        }

        private boolean openMore() {
            int position = getBindingAdapterPosition();
            if (position == RecyclerView.NO_POSITION) return false;
            PodcastEpisode podcastEpisode = podcastEpisodes.get(position);

            if ("completed".equals(podcastEpisode.getStatus())) {
                Bundle bundle = new Bundle();
                bundle.putParcelable(Constants.PODCAST_OBJECT, podcastEpisodes.get(position));

                click.onPodcastEpisodeLongClick(bundle);

                return true;
            }

            return false;
        }

        public void requestDownload() {
            int position = getBindingAdapterPosition();
            if (position == RecyclerView.NO_POSITION) return;
            PodcastEpisode podcastEpisode = podcastEpisodes.get(position);

            if (!"completed".equals(podcastEpisode.getStatus())) {
                Bundle bundle = new Bundle();
                bundle.putParcelable(Constants.PODCAST_OBJECT, podcastEpisodes.get(position));

                click.onPodcastEpisodeAltClick(bundle);
            }
        }
    }

    public void sort(String order) {
        if (podcastEpisodesFull == null) {
            podcastEpisodesFull = Collections.emptyList();
        }

        switch (order) {
            case Constants.PODCAST_FILTER_BY_DOWNLOAD:
                podcastEpisodes = podcastEpisodesFull.stream()
                        .filter(podcastEpisode -> podcastEpisode != null && Objects.equals(podcastEpisode.getStatus(), "completed"))
                        .collect(Collectors.toList());
                break;
            case Constants.PODCAST_FILTER_BY_ALL:
                podcastEpisodes = podcastEpisodesFull;
                break;
        }

        if (podcastEpisodes == null) {
            podcastEpisodes = Collections.emptyList();
        }

        notifyDataSetChanged();
    }
}
