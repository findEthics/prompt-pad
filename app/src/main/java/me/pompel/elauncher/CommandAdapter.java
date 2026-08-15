package me.pompel.elauncher;

import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import java.util.ArrayList;
import java.util.List;

/** Renders command-search feedback without attaching any execution side effects. */
public class CommandAdapter extends RecyclerView.Adapter<CommandAdapter.CommandViewHolder> {
    private final List<Row> rows = new ArrayList<>();

    public void submit(CommandQueryClassifier.Result result) {
        rows.clear();
        switch (result.getDisplayState()) {
            case COMMAND_HELP:
                rows.add(new Row(result.getMessage(), ""));
                addCommands(result.getCommands());
                break;
            case SUGGESTION:
                rows.add(new Row(result.getMessage(), ""));
                addCommands(result.getCommands());
                break;
            case PREVIEW:
                String query = "!" + result.getCommand().getName();
                if (!result.getArguments().isEmpty()) query += " " + result.getArguments();
                rows.add(new Row(query, result.getMessage() + "\n" + result.getSyntaxHint()));
                break;
            case VALIDATION_ERROR:
                rows.add(new Row(result.getMessage(), result.getSyntaxHint()));
                break;
            case UNKNOWN_COMMAND:
                rows.add(new Row(result.getMessage(), "Type one of these commands:"));
                addCommands(result.getCommands());
                break;
            case APP_RESULTS:
                break;
        }
        notifyDataSetChanged();
    }

    private void addCommands(List<CommandQueryClassifier.Command> commands) {
        for (CommandQueryClassifier.Command command : commands) {
            rows.add(new Row("!" + command.getName(), command.getSyntaxHint()));
        }
    }

    @NonNull
    @Override
    public CommandViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View view = LayoutInflater.from(parent.getContext())
                .inflate(R.layout.command_list_item, parent, false);
        return new CommandViewHolder(view);
    }

    @Override
    public void onBindViewHolder(@NonNull CommandViewHolder holder, int position) {
        Row row = rows.get(position);
        holder.title.setText(row.title);
        holder.detail.setText(row.detail);
        holder.detail.setVisibility(row.detail.isEmpty() ? View.GONE : View.VISIBLE);
    }

    @Override
    public int getItemCount() {
        return rows.size();
    }

    static class CommandViewHolder extends RecyclerView.ViewHolder {
        private final TextView title;
        private final TextView detail;

        CommandViewHolder(@NonNull View itemView) {
            super(itemView);
            title = itemView.findViewById(R.id.command_title);
            detail = itemView.findViewById(R.id.command_detail);
        }
    }

    private static class Row {
        private final String title;
        private final String detail;

        Row(String title, String detail) {
            this.title = title;
            this.detail = detail;
        }
    }
}
