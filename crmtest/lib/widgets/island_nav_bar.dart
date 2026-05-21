import 'package:flutter/material.dart';

// ────────────────────────────────────────────────────────────────────────────
//  IslandNavBar — Flutter port of the native IslandNavBar.kt
//
//  Design:
//  • Floating white pill with deep CardView shadow
//  • Animated orange bubble slides under selected item
//  • Center item elevated above the pill — glowing orange circle
//  • Selection animates: icon springs up, label fades in
// ────────────────────────────────────────────────────────────────────────────

class IslandNavBar extends StatefulWidget {
  final int selectedIndex;
  final ValueChanged<int> onTabChanged;

  const IslandNavBar({
    super.key,
    required this.selectedIndex,
    required this.onTabChanged,
  });

  @override
  State<IslandNavBar> createState() => _IslandNavBarState();
}

class _IslandNavBarState extends State<IslandNavBar>
    with TickerProviderStateMixin {
  late List<AnimationController> _scaleControllers;
  late List<Animation<double>> _scaleAnims;
  late List<AnimationController> _labelControllers;
  late List<Animation<double>> _labelAnims;
  late AnimationController _bubbleController;
  late Animation<double> _bubbleX;
  late AnimationController _centerGlowController;
  late Animation<double> _centerGlowAnim;

  static const _items = [
    _NavItem(Icons.dashboard_rounded, 'Home'),
    _NavItem(Icons.people_alt_rounded, 'Leads'),
    _NavItem(Icons.call_rounded, 'Calls'),           // center
    _NavItem(Icons.calendar_month_rounded, 'Calendar'),
    _NavItem(Icons.access_time_rounded, 'Attend'),
  ];

  int get _count => _items.length;

  @override
  void initState() {
    super.initState();

    _scaleControllers = List.generate(
      _count,
      (i) => AnimationController(
        vsync: this,
        duration: const Duration(milliseconds: 350),
      ),
    );
    _scaleAnims = _scaleControllers
        .map((c) => Tween<double>(begin: 1.0, end: 1.25).animate(
              CurvedAnimation(parent: c, curve: Curves.elasticOut),
            ))
        .toList();

    _labelControllers = List.generate(
      _count,
      (i) => AnimationController(
        vsync: this,
        duration: const Duration(milliseconds: 200),
      ),
    );
    _labelAnims = _labelControllers
        .map((c) => CurvedAnimation(parent: c, curve: Curves.easeIn))
        .toList();

    _bubbleController = AnimationController(
      vsync: this,
      duration: const Duration(milliseconds: 350),
    );
    _bubbleX = Tween<double>(begin: 0, end: 0).animate(
      CurvedAnimation(parent: _bubbleController, curve: Curves.easeOutCubic),
    );

    _centerGlowController = AnimationController(
      vsync: this,
      duration: const Duration(milliseconds: 1500),
    )..repeat(reverse: true);
    _centerGlowAnim = Tween<double>(begin: 0.3, end: 0.9).animate(
      CurvedAnimation(parent: _centerGlowController, curve: Curves.easeInOut),
    );

    // Set initial selection
    WidgetsBinding.instance.addPostFrameCallback((_) {
      _selectIndex(widget.selectedIndex, animate: false);
    });
  }

  @override
  void didUpdateWidget(IslandNavBar oldWidget) {
    super.didUpdateWidget(oldWidget);
    if (oldWidget.selectedIndex != widget.selectedIndex) {
      _selectIndex(widget.selectedIndex, animate: true);
    }
  }

  void _selectIndex(int index, {required bool animate}) {
    for (int i = 0; i < _count; i++) {
      if (i == index) {
        _scaleControllers[i].forward(from: 0);
        _labelControllers[i].forward();
      } else {
        _scaleControllers[i].reverse();
        _labelControllers[i].reverse();
      }
    }
  }

  @override
  void dispose() {
    for (final c in _scaleControllers) c.dispose();
    for (final c in _labelControllers) c.dispose();
    _bubbleController.dispose();
    _centerGlowController.dispose();
    super.dispose();
  }

  @override
  Widget build(BuildContext context) {
    return Padding(
      padding: const EdgeInsets.symmetric(horizontal: 16).copyWith(bottom: 14),
      child: Stack(
        alignment: Alignment.bottomCenter,
        clipBehavior: Clip.none,
        children: [
          // ── Pill card ──────────────────────────────────────────────────
          Material(
            elevation: 20,
            borderRadius: BorderRadius.circular(40),
            color: Colors.white,
            shadowColor: Colors.black26,
            child: Container(
              height: 68,
              decoration: BoxDecoration(
                borderRadius: BorderRadius.circular(40),
              ),
              child: Row(
                children: List.generate(_count, (i) {
                  final isCenter = i == 2;
                  final isSelected = widget.selectedIndex == i;
                  return Expanded(
                    child: isCenter
                        ? _buildCenterItem(i, isSelected)
                        : _buildRegularItem(i, isSelected),
                  );
                }),
              ),
            ),
          ),
        ],
      ),
    );
  }

  // ── Regular item ────────────────────────────────────────────────────────
  Widget _buildRegularItem(int index, bool isSelected) {
    return GestureDetector(
      onTap: () {
        _selectIndex(index, animate: true);
        widget.onTabChanged(index);
      },
      behavior: HitTestBehavior.opaque,
      child: AnimatedBuilder(
        animation: Listenable.merge([_scaleAnims[index], _labelAnims[index]]),
        builder: (_, __) {
          final scale = isSelected ? _scaleAnims[index].value : 1.0;
          final labelOpacity = isSelected ? _labelAnims[index].value : 0.0;

          return Column(
            mainAxisAlignment: MainAxisAlignment.center,
            children: [
              // Orange bubble behind icon
              Stack(
                alignment: Alignment.center,
                children: [
                  if (isSelected)
                    AnimatedContainer(
                      duration: const Duration(milliseconds: 300),
                      width: 52,
                      height: 36,
                      decoration: BoxDecoration(
                        color: const Color(0xFFFF7043).withValues(alpha: 0.12),
                        borderRadius: BorderRadius.circular(20),
                      ),
                    ),
                  Transform.translate(
                    offset: Offset(0, isSelected ? -4 : 0),
                    child: Transform.scale(
                      scale: scale,
                      child: Icon(
                        _items[index].icon,
                        color: isSelected
                            ? const Color(0xFFFF7043)
                            : Colors.grey.shade400,
                        size: 24,
                      ),
                    ),
                  ),
                ],
              ),
              FadeTransition(
                opacity: _labelAnims[index],
                child: Text(
                  _items[index].label,
                  style: TextStyle(
                    fontSize: 9,
                    fontWeight: FontWeight.w700,
                    color: isSelected
                        ? const Color(0xFFFF7043)
                        : Colors.grey.shade400,
                  ),
                ),
              ),
            ],
          );
        },
      ),
    );
  }

  // ── Center item (elevated orange circle) ────────────────────────────────
  Widget _buildCenterItem(int index, bool isSelected) {
    return GestureDetector(
      onTap: () {
        _selectIndex(index, animate: true);
        widget.onTabChanged(index);
      },
      behavior: HitTestBehavior.opaque,
      child: SizedBox(
        width: double.infinity,
        child: Stack(
          alignment: Alignment.center,
          clipBehavior: Clip.none,
          children: [
            // Pulsing glow ring
            AnimatedBuilder(
              animation: _centerGlowAnim,
              builder: (_, __) => Transform.scale(
                scale: 1.0 + (_centerGlowAnim.value * 0.3),
                child: Container(
                  width: 58,
                  height: 58,
                  decoration: BoxDecoration(
                    shape: BoxShape.circle,
                    color: const Color(0xFFFF7043)
                        .withValues(alpha: _centerGlowAnim.value * 0.5),
                  ),
                ),
              ),
            ),
            // Orange circle
            Positioned(
              top: -10,
              child: Container(
                width: 54,
                height: 54,
                decoration: BoxDecoration(
                  shape: BoxShape.circle,
                  gradient: const RadialGradient(
                    colors: [Color(0xFFFF8C5A), Color(0xFFFF5722)],
                  ),
                  boxShadow: [
                    BoxShadow(
                      color: const Color(0xFFFF5722).withValues(alpha: 0.5),
                      blurRadius: 12,
                      offset: const Offset(0, 4),
                    ),
                  ],
                ),
                child: const Icon(
                  Icons.call_rounded,
                  color: Colors.white,
                  size: 26,
                ),
              ),
            ),
            // Label at bottom
            Positioned(
              bottom: 4,
              child: FadeTransition(
                opacity: _labelAnims[index],
                child: const Text(
                  'Calls',
                  style: TextStyle(
                    fontSize: 8,
                    fontWeight: FontWeight.w700,
                    color: Color(0xFFFF7043),
                  ),
                ),
              ),
            ),
          ],
        ),
      ),
    );
  }
}

class _NavItem {
  final IconData icon;
  final String label;
  const _NavItem(this.icon, this.label);
}
