// CallRecord model — mirrors the native Room entity
class CallRecord {
  final int? id;
  final String name;
  final String phone;
  final String status;
  final int duration;      // seconds
  final int calledAt;      // epoch ms
  final String calledBy;

  const CallRecord({
    this.id,
    required this.name,
    required this.phone,
    required this.status,
    required this.duration,
    required this.calledAt,
    required this.calledBy,
  });

  Map<String, dynamic> toMap() => {
    'id': id,
    'name': name,
    'phone': phone,
    'status': status,
    'duration': duration,
    'calledAt': calledAt,
    'calledBy': calledBy,
  };

  factory CallRecord.fromMap(Map<String, dynamic> m) => CallRecord(
    id: m['id'] as int?,
    name: m['name'] as String? ?? '',
    phone: m['phone'] as String? ?? '',
    status: m['status'] as String? ?? 'Pending',
    duration: m['duration'] as int? ?? 0,
    calledAt: m['calledAt'] as int? ?? 0,
    calledBy: m['calledBy'] as String? ?? '',
  );
}
