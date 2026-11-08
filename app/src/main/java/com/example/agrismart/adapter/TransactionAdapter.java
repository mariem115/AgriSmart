package com.example.agrismart.adapter;

import android.content.Context;
import android.content.res.ColorStateList;
import android.view.LayoutInflater;
import android.view.ViewGroup;

import androidx.annotation.NonNull;
import androidx.core.content.ContextCompat;
import androidx.recyclerview.widget.RecyclerView;

import com.example.agrismart.AppHelper;
import com.example.agrismart.R;
import com.example.agrismart.databinding.ItemTransactionBinding;
import com.example.agrismart.model.Transaction;

import java.util.ArrayList;
import java.util.List;

/**
 * Adaptateur de la liste des dernières opérations financières.
 */
public class TransactionAdapter extends RecyclerView.Adapter<TransactionAdapter.TxViewHolder> {

    public interface OnTransactionClickListener {
        void onTransactionClick(Transaction transaction);
    }

    private final List<Transaction> transactions = new ArrayList<>();
    private final OnTransactionClickListener listener;

    public TransactionAdapter(OnTransactionClickListener listener) {
        this.listener = listener;
    }

    public void setTransactions(List<Transaction> newTransactions) {
        transactions.clear();
        transactions.addAll(newTransactions);
        notifyDataSetChanged();
    }

    @NonNull
    @Override
    public TxViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        return new TxViewHolder(ItemTransactionBinding.inflate(
                LayoutInflater.from(parent.getContext()), parent, false));
    }

    @Override
    public void onBindViewHolder(@NonNull TxViewHolder holder, int position) {
        Transaction tx = transactions.get(position);
        Context context = holder.itemView.getContext();
        boolean income = Transaction.TYPE_INCOME.equals(tx.getType());

        int strong = ContextCompat.getColor(context, income ? R.color.green_primary : R.color.red);
        int soft = ContextCompat.getColor(context, income ? R.color.green_soft : R.color.red_soft);
        holder.binding.ivIcon.setImageResource(income ? R.drawable.ic_add : R.drawable.ic_remove);
        holder.binding.ivIcon.setImageTintList(ColorStateList.valueOf(strong));
        holder.binding.ivIcon.setBackgroundTintList(ColorStateList.valueOf(soft));

        holder.binding.tvLabel.setText(tx.getLabel());
        String category = AppHelper.labelOf(context, R.array.finance_category_codes,
                R.array.finance_category_labels, tx.getCategory());
        String parcel = (tx.getParcelName() == null || tx.getParcelName().isEmpty())
                ? context.getString(R.string.finance_general) : tx.getParcelName();
        holder.binding.tvDetails.setText(context.getString(R.string.finance_tx_details,
                parcel, category, AppHelper.formatDate(tx.getDate())));

        String sign = income ? "+ " : "- ";
        holder.binding.tvAmount.setText(sign + context.getString(R.string.money_value,
                AppHelper.formatMoney(tx.getAmount())));
        holder.binding.tvAmount.setTextColor(strong);

        holder.itemView.setOnClickListener(v -> listener.onTransactionClick(tx));
    }

    @Override
    public int getItemCount() {
        return transactions.size();
    }

    static class TxViewHolder extends RecyclerView.ViewHolder {
        final ItemTransactionBinding binding;

        TxViewHolder(ItemTransactionBinding binding) {
            super(binding.getRoot());
            this.binding = binding;
        }
    }
}
