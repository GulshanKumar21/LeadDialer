import 'package:flutter/material.dart';
import '../models/lead.dart';
import '../services/call_db_service.dart';
import 'package:flutter_phone_direct_caller/flutter_phone_direct_caller.dart';

// Simple Leads screen
class LeadsScreen extends StatefulWidget {
  const LeadsScreen({super.key});

  @override
  State<LeadsScreen> createState() => _LeadsScreenState();
}

class _LeadsScreenState extends State<LeadsScreen> {
  List<Lead> _leads = [];
  List<Lead> _filtered = [];
  final _search = TextEditingController();
  bool _loading = true;

  @override
  void initState() {
    super.initState();
    _load();
    _search.addListener(() {
      final q = _search.text.toLowerCase();
      setState(() {
        _filtered = q.isEmpty
            ? _leads
            : _leads.where((l) =>
                l.name.toLowerCase().contains(q) ||
                l.phone.contains(q)).toList();
      });
    });
  }

  Future<void> _load() async {
    final leads = await AppDbService().getAllLeads();
    if (mounted) setState(() { _leads = leads; _filtered = leads; _loading = false; });
  }

  Color _statusColor(String s) => switch (s) {
    'Connected'      => const Color(0xFF2E7D32),
    'Interested'     => const Color(0xFF1565C0),
    'Busy'           => const Color(0xFFE65100),
    'Not Connected'  => const Color(0xFF757575),
    'Not Interested' => const Color(0xFFC62828),
    _                => const Color(0xFFFF7043),
  };

  @override
  Widget build(BuildContext context) {
    if (_loading) return const Center(child: CircularProgressIndicator(color: Color(0xFFFF7043)));

    return Column(
      children: [
        // Search bar
        Padding(
          padding: const EdgeInsets.all(12),
          child: Card(
            elevation: 6,
            shape: RoundedRectangleBorder(borderRadius: BorderRadius.circular(30)),
            child: Padding(
              padding: const EdgeInsets.symmetric(horizontal: 16, vertical: 4),
              child: TextField(
                controller: _search,
                decoration: const InputDecoration(
                  hintText: 'Search by name or number',
                  border: InputBorder.none,
                  prefixIcon: Icon(Icons.search, color: Color(0xFF993800)),
                ),
              ),
            ),
          ),
        ),

        // Lead list
        Expanded(
          child: _filtered.isEmpty
              ? const Center(
                  child: Text(
                    'No leads found.\nImport leads from Excel!',
                    textAlign: TextAlign.center,
                    style: TextStyle(color: Color(0xFFC47A50), fontSize: 15),
                  ),
                )
              : ListView.builder(
                  padding: const EdgeInsets.symmetric(horizontal: 12),
                  itemCount: _filtered.length,
                  itemBuilder: (_, i) => _buildCard(_filtered[i]),
                ),
        ),
      ],
    );
  }

  Widget _buildCard(Lead lead) {
    return Card(
      margin: const EdgeInsets.only(bottom: 10),
      elevation: 5,
      shape: RoundedRectangleBorder(borderRadius: BorderRadius.circular(16)),
      child: ListTile(
        contentPadding: const EdgeInsets.symmetric(horizontal: 16, vertical: 8),
        leading: CircleAvatar(
          backgroundColor: const Color(0xFFFF7043),
          child: Text(
            lead.name.isNotEmpty ? lead.name[0].toUpperCase() : '?',
            style: const TextStyle(color: Colors.white, fontWeight: FontWeight.bold),
          ),
        ),
        title: Text(lead.name,
            style: const TextStyle(fontWeight: FontWeight.bold, fontSize: 15)),
        subtitle: Column(
          crossAxisAlignment: CrossAxisAlignment.start,
          children: [
            Text(lead.phone, style: const TextStyle(color: Colors.grey, fontSize: 12)),
            Text(lead.status,
                style: TextStyle(
                    color: _statusColor(lead.status),
                    fontWeight: FontWeight.bold,
                    fontSize: 12)),
          ],
        ),
        trailing: GestureDetector(
          onTap: () => FlutterPhoneDirectCaller.callNumber(lead.phone),
          child: Container(
            width: 44,
            height: 44,
            decoration: BoxDecoration(
              color: const Color(0xFF22C55E),
              borderRadius: BorderRadius.circular(22),
              boxShadow: [
                BoxShadow(
                  color: const Color(0xFF22C55E).withValues(alpha: 0.4),
                  blurRadius: 8,
                  offset: const Offset(0, 2),
                ),
              ],
            ),
            child: const Icon(Icons.call_rounded, color: Colors.white, size: 22),
          ),
        ),
      ),
    );
  }
}
