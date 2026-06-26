// CallRecord model — mirrors the native Room entity
class CallRecord {
  final int? id;
  final String name;
  final String phone;
  final String status;
  final int duration;      // seconds
  final int calledAt;      // epoch ms
  final String calledBy;
  final String note;

  CallRecord({
    this.id,
    required this.name,
    required String phone,
    required this.status,
    required this.duration,
    required this.calledAt,
    required this.calledBy,
    this.note = '',
  }) : phone = sanitizePhone(phone);

  static String sanitizePhone(String phone) {
    var clean = phone.replaceAll(RegExp(r'[^0-9]'), '');
    if (clean.startsWith('91') && clean.length > 10) {
      clean = clean.substring(2);
    }
    return clean;
  }

  Map<String, dynamic> toMap() => {
    'id': id,
    'name': name,
    'phone': phone,
    'status': status,
    'duration': duration,
    'calledAt': calledAt,
    'calledBy': calledBy,
    'note': note,
  };

  factory CallRecord.fromMap(Map<String, dynamic> m) => CallRecord(
    id: m['id'] as int?,
    name: m['name'] as String? ?? '',
    phone: m['phone'] as String? ?? '',
    status: m['status'] as String? ?? 'Pending',
    duration: m['duration'] as int? ?? 0,
    calledAt: m['calledAt'] as int? ?? 0,
    calledBy: m['calledBy'] as String? ?? '',
    note: m['note'] as String? ?? '',
  );

  CallRecord copyWith({
    int? id,
    String? name,
    String? phone,
    String? status,
    int? duration,
    int? calledAt,
    String? calledBy,
    String? note,
  }) => CallRecord(
    id: id ?? this.id,
    name: name ?? this.name,
    phone: phone ?? this.phone,
    status: status ?? this.status,
    duration: duration ?? this.duration,
    calledAt: calledAt ?? this.calledAt,
    calledBy: calledBy ?? this.calledBy,
    note: note ?? this.note,
  );
}
