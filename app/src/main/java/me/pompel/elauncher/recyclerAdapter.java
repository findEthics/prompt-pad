package me.pompel.elauncher;

import android.annotation.SuppressLint;
import android.text.SpannableString;
import android.text.Spanned;
import android.text.style.UnderlineSpan;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Filter;
import android.widget.Filterable;
import android.widget.TextView;
import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;


public class recyclerAdapter extends RecyclerView.Adapter<recyclerAdapter.AppViewHolder> implements Filterable {
    private static final int MIN_AUTO_LAUNCH_QUERY_LENGTH = 4;
    private final ArrayList<App> appList;
    private ArrayList<App> appListFiltered;
    private final RecyclerViewClickListener listener;
    private String activeQuery = "";
    private boolean filteringEnabled = true;

    public recyclerAdapter(ArrayList<App> appList, RecyclerViewClickListener listener) {
        this.appList = appList;
        this.appListFiltered = new ArrayList<>();
        this.listener = listener;
    }

    public void filter(CharSequence query) {
        filteringEnabled = true;
        activeQuery = query.toString();
        getFilter().filter(query);
    }

    public void pauseFiltering() {
        filteringEnabled = false;
    }

    @Override
    public Filter getFilter() {
        return new Filter() {
            @Override
            protected FilterResults performFiltering(CharSequence charSequence) {
                String query = charSequence.toString().toLowerCase(Locale.ROOT);
                FilterResults results = new FilterResults();
                List<App> filteredApps = new ArrayList<>();

                if (!query.isEmpty()) {
                    for (App app : appList) {
                        if (app.appName.toString().toLowerCase(Locale.ROOT).contains(query)) {
                            filteredApps.add(app);
                        }
                    }
                }

                results.count = filteredApps.size();
                results.values = filteredApps;
                return results;
            }

            @SuppressLint("NotifyDataSetChanged")
            @Override
            protected void publishResults(CharSequence charSequence, FilterResults filterResults) {
                if (!filteringEnabled || !activeQuery.equals(charSequence.toString())) return;
                appListFiltered = (ArrayList<App>)filterResults.values;

                for (App app : appListFiltered) {
                    String appName = app.appName.toString();
                    String normalizedAppName = appName.toLowerCase(Locale.ROOT);
                    String query = charSequence.toString().toLowerCase(Locale.ROOT);
                    int queryIndex = normalizedAppName.indexOf(query);
                    if (queryIndex >= 0 && !query.isEmpty()) {
                        app.appName.setSpan(new UnderlineSpan(), queryIndex,
                                queryIndex + query.length(), Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
                    }

                    // if an exact match, exit and click on it
                    if (normalizedAppName.equals(query)
                            && charSequence.toString().trim().length() >= MIN_AUTO_LAUNCH_QUERY_LENGTH) {
                        listener.onClick(app);
                        break;
                    }
                }

                if (appListFiltered.size() == 1
                        && charSequence.toString().trim().length() >= MIN_AUTO_LAUNCH_QUERY_LENGTH) {
                    listener.onClick(appListFiltered.get(0));
                }
                notifyDataSetChanged();
            }
        };
    }

    public class AppViewHolder extends RecyclerView.ViewHolder implements View.OnClickListener, View.OnLongClickListener {
        private final TextView nameText;

        @Override
        public void onClick(View view) { listener.onClick(appListFiltered.get(getAbsoluteAdapterPosition())); }

        @Override
        public boolean onLongClick(View view) {
            listener.onLongClick(appListFiltered.get(getAbsoluteAdapterPosition()));
            return true;
        }

        public AppViewHolder(final View view) {
            super(view);
            nameText = view.findViewById(R.id.app_name);
            view.setOnClickListener(this);
            view.setOnLongClickListener(this);
        }
    }

    @NonNull
    @Override
    public recyclerAdapter.AppViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        return new AppViewHolder(LayoutInflater.from(parent.getContext()).inflate(R.layout.list_items, parent, false));
    }

    @Override
    public void onBindViewHolder(@NonNull recyclerAdapter.AppViewHolder holder, int position) {
        SpannableString appName = appListFiltered.get(position).appName;
        holder.nameText.setText(appName);

        // remove all the spans after the string has been set
        Object[] spans = appName.getSpans(0, appName.length(), Object.class);
        for (Object span : spans) {
            appName.removeSpan(span);
        }
    }

    @Override
    public int getItemCount() {
        return appListFiltered.size();
    }

    public interface RecyclerViewClickListener {
        void onClick(App app);
        void onLongClick(App app);

    }
}
