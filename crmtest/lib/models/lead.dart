// Lead model — mirrors the native Room entity
class Lead {
  final int id;
  final String name;
  final String phone;
  final String status;
  final bool isHotLead;
  final bool salesDone;
  final String? collegeName;
  final String? collegeCity;
  final String? notes;
  final String? calledBy;
  final int? calledAt;
  final String? firestoreId;
  final int duration;

  Lead({
    this.id = 0,
    required this.name,
    required String phone,
    this.status = 'Pending',
    this.isHotLead = false,
    this.salesDone = false,
    this.collegeName,
    this.collegeCity,
    this.notes,
    this.calledBy,
    this.calledAt,
    this.firestoreId,
    this.duration = 0,
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
    'isHotLead': isHotLead ? 1 : 0,
    'salesDone': salesDone ? 1 : 0,
    'collegeName': collegeName,
    'collegeCity': collegeCity,
    'notes': notes,
    'calledBy': calledBy,
    'calledAt': calledAt,
    'firestoreId': firestoreId,
    'duration': duration,
  };

  factory Lead.fromMap(Map<String, dynamic> m) => Lead(
    id: m['id'] as int? ?? 0,
    name: m['name'] as String? ?? '',
    phone: m['phone'] as String? ?? '',
    status: m['status'] as String? ?? 'Pending',
    isHotLead: (m['isHotLead'] as int? ?? 0) == 1,
    salesDone: (m['salesDone'] as int? ?? 0) == 1,
    collegeName: m['collegeName'] as String?,
    collegeCity: m['collegeCity'] as String?,
    notes: m['notes'] as String?,
    calledBy: m['calledBy'] as String?,
    calledAt: m['calledAt'] as int?,
    firestoreId: m['firestoreId'] as String?,
    duration: m['duration'] as int? ?? 0,
  );

  Lead copyWith({
    int? id,
    String? status,
    bool? isHotLead,
    bool? salesDone,
    String? notes,
    int? calledAt,
    String? firestoreId,
    int? duration,
    String? collegeName,
    String? collegeCity,
  }) => Lead(
    id: id ?? this.id,
    name: name,
    phone: phone,
    status: status ?? this.status,
    isHotLead: isHotLead ?? this.isHotLead,
    salesDone: salesDone ?? this.salesDone,
    collegeName: collegeName ?? this.collegeName,
    collegeCity: collegeCity ?? this.collegeCity,
    notes: notes ?? this.notes,
    calledBy: calledBy,
    calledAt: calledAt ?? this.calledAt,
    firestoreId: firestoreId ?? this.firestoreId,
    duration: duration ?? this.duration,
  );
}
