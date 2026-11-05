package com.thkim.golfyardage.ui.scorecard

import android.app.AlertDialog
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Toast
import androidx.fragment.app.Fragment
import androidx.recyclerview.widget.LinearLayoutManager
import com.google.mlkit.vision.codescanner.GmsBarcodeScanning
import com.thkim.golfyardage.R
import com.thkim.golfyardage.data.RoundHistoryStore
import com.thkim.golfyardage.data.model.RoundRecord
import com.thkim.golfyardage.databinding.FragmentScorecardBinding
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class ScorecardFragment : Fragment() {

    private var _binding: FragmentScorecardBinding? = null
    private val binding get() = _binding!!
    private lateinit var adapter: RoundHistoryAdapter

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentScorecardBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        adapter = RoundHistoryAdapter(emptyList()) { record -> confirmDelete(record) }
        binding.recyclerRounds.layoutManager = LinearLayoutManager(requireContext())
        binding.recyclerRounds.adapter = adapter

        binding.btnImportQr.setOnClickListener { launchQrScanner() }

        loadRounds()
    }

    override fun onResume() {
        super.onResume()
        loadRounds()
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }

    private fun loadRounds() {
        val records = RoundHistoryStore.loadAll(requireContext())
        adapter.updateItems(records)
        binding.textEmpty.visibility = if (records.isEmpty()) View.VISIBLE else View.GONE
    }

    private fun confirmDelete(record: RoundRecord) {
        val context = requireContext()
        val dateText = SimpleDateFormat("yyyy.MM.dd", Locale.KOREA).format(Date(record.dateMillis))
        AlertDialog.Builder(context)
            .setTitle(R.string.delete_round_confirm_title)
            .setMessage(getString(R.string.delete_round_confirm_message, record.courseName, dateText))
            .setPositiveButton(android.R.string.ok) { _, _ ->
                RoundHistoryStore.delete(context, record.id)
                loadRounds()
            }
            .setNegativeButton(android.R.string.cancel, null)
            .show()
    }

    private fun launchQrScanner() {
        val context = requireContext()
        GmsBarcodeScanning.getClient(context).startScan()
            .addOnSuccessListener { barcode ->
                val raw = barcode.rawValue
                if (raw != null) handleScanResult(raw)
            }
            .addOnCanceledListener {
                Toast.makeText(context, getString(R.string.qr_scan_cancelled), Toast.LENGTH_SHORT).show()
            }
            .addOnFailureListener { e ->
                Toast.makeText(context, e.message, Toast.LENGTH_SHORT).show()
            }
    }

    private fun handleScanResult(raw: String) {
        val context = requireContext()
        when (val result = ScoreQrParser.parse(raw)) {
            is QrParseResult.UrlLink -> {
                AlertDialog.Builder(context)
                    .setTitle(R.string.qr_url_title)
                    .setMessage(getString(R.string.qr_url_message, result.url))
                    .setPositiveButton(R.string.qr_open_link) { _, _ ->
                        startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(result.url)))
                    }
                    .setNegativeButton(android.R.string.cancel, null)
                    .show()
            }
            is QrParseResult.Parsed -> {
                RoundHistoryStore.saveRecord(context, result.record)
                loadRounds()
                Toast.makeText(
                    context,
                    getString(R.string.qr_imported_message, result.record.courseName, result.record.totalStrokes),
                    Toast.LENGTH_LONG
                ).show()
            }
            is QrParseResult.Unrecognized -> {
                AlertDialog.Builder(context)
                    .setTitle(R.string.qr_unrecognized_title)
                    .setMessage(getString(R.string.qr_unrecognized_message, result.rawText))
                    .setPositiveButton(android.R.string.ok, null)
                    .show()
            }
        }
    }
}
