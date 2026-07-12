/* cpu_soak.c — self-checking CPU/cache/memory soak test
 * Screen: black = running, green = all passes clean so far, red = latched failure.
 * Per-test progress on debug_num (7-seg): 0x01..0x0F. Pass counter: 0xA000|n.
 * On failure: 4 debug_num values = test_id, where, expected, actual (first 8 fails).
 */

typedef unsigned int   u32;
typedef signed   int   s32;
typedef unsigned char  u8;
typedef unsigned short u16;

__attribute__((naked, section(".text.boot"))) void _start(void) {
    __asm__ volatile(
        "li sp, 0x7000000\n"
        "call main\n"
        "1: j 1b\n"
    );
}

static void debug_num(u32 value) {
    *((volatile u32*)0x70000008) = value;
}
static volatile u8* const uart_tx = (volatile u8*)0x08000034;
static void debug_log(const char* c) {
    while (*c) {
        *uart_tx = (u8)*c;
        *((volatile u32*)0x70000000) = (u32)*c;
        c++;
    }
}

#define FB     ((volatile u32*)0x10000000)
#define IMG_W  320
#define IMG_H  240
#define GREEN  0x1Cu
#define RED    0xE0u
static void paint(u32 color) { for (u32 i = 0; i < IMG_W*IMG_H; i++) FB[i] = color; }

/* NOTE: .bss is NOT zeroed with -nostdlib; every static below is
 * explicitly initialized at runtime before use. */
static u32 g_errors;
static u32 g_pass;
static u32 rng_state;
static u32 rnd(void) { u32 x = rng_state; x ^= x<<13; x ^= x>>17; x ^= x<<5; return rng_state = x; }

static void fail(u32 id, u32 where, u32 exp, u32 act) {
    if (g_errors < 8) {
        debug_log("FAIL id/where/exp/act:\n");
        debug_num(id); debug_num(where); debug_num(exp); debug_num(act);
    }
    if (g_errors == 0) paint(RED);
    g_errors++;
}

/* scratch regions — all well inside the 27-bit window, above the code image */
#define SCRATCHW ((volatile u32*)0x00200000u)

/* ================= T01: ALU (algebraic identities + known answers) ======= */
static void t01_alu(void) {
    debug_num(0x01);
    for (u32 i = 0; i < 2000; i++) {
        u32 a = rnd(), b = rnd();
        if (((a ^ b) ^ b) != a)        fail(0x01, i, a, (a^b)^b);
        if (((a + b) - b) != a)        fail(0x01, 0x1000+i, a, (a+b)-b);
        if ((~(a & b)) != (~a | ~b))   fail(0x01, 0x2000+i, ~(a&b), ~a|~b);
        if ((~(a | b)) != (~a & ~b))   fail(0x01, 0x3000+i, ~(a|b), ~a&~b);
        s32 sa = (s32)a, sb = (s32)b;
        if (((a  < b ) ? 1u:0u) != ((b  > a ) ? 1u:0u)) fail(0x01, 0x4000+i, a, b);
        if (((sa < sb) ? 1u:0u) != ((sb > sa) ? 1u:0u)) fail(0x01, 0x5000+i, a, b);
        if ((sa < sb) && (sb < sa))                     fail(0x01, 0x6000+i, a, b);
    }
    volatile u32 x = 0xFFFFFFFFu;
    if (x + 1u != 0u)                          fail(0x01, 0x9001, 0, x+1u);
    if (((s32)0x80000000u >> 31) != -1)        fail(0x01, 0x9002, (u32)-1, (u32)((s32)0x80000000u>>31));
    if ((0x80000000u >> 31) != 1u)             fail(0x01, 0x9003, 1, 0x80000000u>>31);
}

/* ================= T02: barrel shifter, all 32 amounts =================== */
static void t02_shift(void) {
    debug_num(0x02);
    for (u32 n = 0; n < 32; n++) {
        volatile u32 vn = n;                 /* force register-amount shifts */
        u32 v = rnd() | 1u;
        u32 a = v << vn;  u32 b = v; for (u32 i=0;i<n;i++) b <<= 1;
        if (a != b) fail(0x02, n, b, a);
        u32 c = v >> vn;  u32 d = v; for (u32 i=0;i<n;i++) d >>= 1;
        if (c != d) fail(0x02, 0x100+n, d, c);
        s32 sv = (s32)(v | 0x80000000u);
        s32 e = sv >> vn; s32 f = sv; for (u32 i=0;i<n;i++) f >>= 1;
        if (e != f) fail(0x02, 0x200+n, (u32)f, (u32)e);
    }
}

/* ================= T03: branch/compare matrix (BEQ/BNE/BLT[U]/BGE[U]) ==== */
static void t03_branch(void) {
    debug_num(0x03);
    static const u32 vals[7]  = {0u,1u,0x7FFFFFFFu,0x80000000u,0xFFFFFFFFu,0x12345678u,0xEDCBA988u};
    static const u8  urank[7] = {0,1,3,4,6,2,5};   /* rank in unsigned order */
    static const u8  srank[7] = {3,4,6,0,2,5,1};   /* rank in signed order   */
    for (u32 i = 0; i < 7; i++) for (u32 j = 0; j < 7; j++) {
        volatile u32 va = vals[i], vb = vals[j];
        u32 a = va, b = vb; s32 sa = (s32)a, sb = (s32)b;
        u32 m = 0;
        if (a == b)  m |= 1;  if (a != b)  m |= 2;
        if (a <  b)  m |= 4;  if (a >= b)  m |= 8;
        if (sa < sb) m |= 16; if (sa >= sb)m |= 32;
        u32 exp = ((i==j)?1u:2u)
                | ((urank[i] < urank[j])?4u:8u)
                | ((srank[i] < srank[j])?16u:32u);
        if (m != exp) fail(0x03, i*8+j, exp, m);
    }
}

/* ================= T04: LB/LBU/LH/LHU sign/zero extension, all offsets === */
static void t04_load_ext(void) {
    debug_num(0x04);
    volatile u8 buf[16] __attribute__((aligned(16)));
    for (u32 i = 0; i < 16; i++) buf[i] = (u8)(0x71u + i*0x1Fu);
    for (u32 i = 0; i < 16; i++) {
        u8 b = (u8)(0x71u + i*0x1Fu);
        u32 lbu = *(volatile u8*)&buf[i];
        if (lbu != b) fail(0x04, i, b, lbu);
        s32 lb = *(volatile signed char*)&buf[i];
        s32 eb = (b & 0x80u) ? (s32)((u32)b | 0xFFFFFF00u) : (s32)b;
        if (lb != eb) fail(0x04, 0x10+i, (u32)eb, (u32)lb);
    }
    for (u32 i = 0; i < 16; i += 2) {
        u32 lo = (u8)(0x71u + i*0x1Fu), hi = (u8)(0x71u + (i+1)*0x1Fu);
        u32 h = (hi << 8) | lo;
        u32 lhu = *(volatile u16*)&buf[i];
        if (lhu != h) fail(0x04, 0x30+i, h, lhu);
        s32 lh = *(volatile short*)&buf[i];
        s32 eh = (h & 0x8000u) ? (s32)(h | 0xFFFF0000u) : (s32)h;
        if (lh != eh) fail(0x04, 0x40+i, (u32)eh, (u32)lh);
    }
}

/* ================= T05: SB/SH read-modify-write, all byte lanes ========== */
static void t05_subword_store(void) {
    debug_num(0x05);
    volatile u32 *w = SCRATCHW;
    for (u32 t = 0; t < 256; t++) {
        volatile u32 *p = &w[rnd() & 0x3FFu];
        *p = 0xA5A5A5A5u;
        u32 v = rnd(), off = rnd() & 3u;
        ((volatile u8*)p)[off] = (u8)v;
        u32 exp = (0xA5A5A5A5u & ~(0xFFu << (off*8))) | ((v & 0xFFu) << (off*8));
        u32 got = *p;                                  /* load right after SB */
        if (got != exp) fail(0x05, t, exp, got);

        *p = 0x5A5A5A5Au;
        u32 h = rnd(), hoff = (rnd() & 1u) * 2u;
        *(volatile u16*)((volatile u8*)p + hoff) = (u16)h;
        exp = (0x5A5A5A5Au & ~(0xFFFFu << (hoff*8))) | ((h & 0xFFFFu) << (hoff*8));
        got = *p;
        if (got != exp) fail(0x05, 0x1000+t, exp, got);
    }
}

/* ================= T06: back-to-back store->load, same line ============== */
static void t06_store_load_hazard(void) {
    debug_num(0x06);
    volatile u32 *p = &SCRATCHW[17];
    for (u32 i = 0; i < 4000; i++) {
        u32 v = rnd();
        p[0] = v;      u32 r0 = p[0]; if (r0 != v)  fail(0x06, i,        v,  r0);
        u32 v2 = ~v;
        p[1] = v2;     u32 r1 = p[0]; if (r1 != v)  fail(0x06, 0x1000+i, v,  r1); /* other word, same line, right after store */
                       u32 r2 = p[1]; if (r2 != v2) fail(0x06, 0x2000+i, v2, r2);
        ((volatile u8*)p)[i & 3] = (u8)v2;
        u32 r3 = ((volatile u8*)p)[i & 3];
        if (r3 != (v2 & 0xFFu)) fail(0x06, 0x3000+i, v2 & 0xFFu, r3);
    }
}

/* ================= T07: dependent load chain (load-use hazard) =========== */
static void t07_pointer_chase(void) {
    debug_num(0x07);
    volatile u32 *w = SCRATCHW + 0x800;            /* 2KB ring > 1KB dcache */
    const u32 N = 512, stride = 0x1EF;             /* odd -> full period    */
    u32 idx = 0;
    for (u32 i = 0; i < N; i++) { u32 nxt = (idx + stride) & (N-1); w[idx] = nxt; idx = nxt; }
    idx = 0; u32 count = 0;
    do { idx = w[idx]; count++; } while (idx != 0 && count <= N);
    if (count != N) fail(0x07, 0, N, count);
}

/* ================= T08: direct-mapped aliasing + dirty writeback ========= */
/* dcache = 64 sets x 16B lines -> addresses 1KB apart collide in a set     */
static void t08_alias_writeback(void) {
    debug_num(0x08);
    volatile u32 *base = (volatile u32*)0x00210000u;
    for (u32 set = 0; set < 64; set++) {
        for (u32 word = 0; word < 4; word++) {
            for (u32 k = 0; k < 8; k++) {          /* each store evicts prev dirty line */
                volatile u32 *p = base + (k*1024u + set*16u)/4u + word;
                *p = (set<<16) ^ (k<<8) ^ word ^ 0xC0DE0000u;
            }
            for (u32 k = 0; k < 8; k++) {          /* each read also evicts a dirty line */
                volatile u32 *p = base + (k*1024u + set*16u)/4u + word;
                u32 exp = (set<<16) ^ (k<<8) ^ word ^ 0xC0DE0000u;
                u32 got = *p;
                if (got != exp) fail(0x08, (set<<8)|(k<<2)|word, exp, got);
            }
        }
        /* full-line dirty -> forced eviction -> full-line readback */
        volatile u32 *A = base + (set*16u)/4u;
        volatile u32 *B = A + 1024u/4u;
        for (u32 wd = 0; wd < 4; wd++) A[wd] = 0x11110000u ^ (set<<4) ^ wd;
        for (u32 wd = 0; wd < 4; wd++) B[wd] = 0x22220000u ^ (set<<4) ^ wd;
        for (u32 wd = 0; wd < 4; wd++) { u32 g=A[wd], e=0x11110000u^(set<<4)^wd; if (g!=e) fail(0x08,0x8000|(set<<2)|wd,e,g); }
        for (u32 wd = 0; wd < 4; wd++) { u32 g=B[wd], e=0x22220000u^(set<<4)^wd; if (g!=e) fail(0x08,0xC000|(set<<2)|wd,e,g); }
    }
}

/* ================= T09: byte storm — scrambled SB over 8KB =============== */
/* Every SB is an RMW with constant miss/evict traffic. Region base shifts  */
/* every pass so failures that depend on placement get swept.               */
static void t09_byte_storm(void) {
    debug_num(0x09);
    u32 shift = (g_pass & 7u) * 528u;
    u32 base  = 0x00230000u + shift;
    volatile u8 *r = (volatile u8*)base;
    const u32 N = 8192;
    u32 idx = 0;
    for (u32 i = 0; i < N; i++) {
        idx = (idx + 0x0C85u) & (N-1);
        u32 a = base + idx;
        r[idx] = (u8)((a ^ (a>>7) ^ (a>>13)) & 0xFFu);
    }
    idx = 0;
    for (u32 i = 0; i < N; i++) {
        idx = (idx + 0x1A2Bu) & (N-1);
        u32 a = base + idx;
        u8 e = (u8)((a ^ (a>>7) ^ (a>>13)) & 0xFFu);
        u8 g = r[idx];
        if (g != e) fail(0x09, idx, e, g);
    }
}

/* ================= T10: byte-copy alignment matrix across lines ========== */
static void byte_copy(volatile u8 *d, const volatile u8 *s, u32 n) { while (n--) *d++ = *s++; }
static void t10_copy_matrix(void) {
    debug_num(0x0A);
    volatile u8 *src = (volatile u8*)0x00240000u;
    volatile u8 *dst = (volatile u8*)0x00242000u;
    for (u32 i = 0; i < 256; i++) src[i] = (u8)rnd();
    for (u32 so = 0; so < 4; so++) for (u32 dofs = 0; dofs < 4; dofs++) {
        u32 n = 61 + so + dofs;                    /* spans several lines */
        for (u32 i = 0; i < n + 8; i++) dst[i] = 0xEE;
        byte_copy(dst + dofs, src + so, n);
        for (u32 i = 0; i < n; i++) {
            u8 g = dst[dofs+i], e = src[so+i];
            if (g != e) fail(0x0A, (so<<10)|(dofs<<8)|i, e, g);
        }
        if (dst[dofs+n] != 0xEE) fail(0x0A, 0xF000|(so<<2)|dofs, 0xEE, dst[dofs+n]);
    }
}

/* ================= T11: stack integrity via recursion + canaries ========= */
static u32 rec(u32 n) {
    volatile u32 local[4];
    local[0]=n; local[1]=n^0xDEADu; local[2]=~n; local[3]=n<<3;
    u32 r = (n == 0) ? 0u : rec(n-1) + n;
    if (local[0]!=n || local[1]!=(n^0xDEADu) || local[2]!=~n || local[3]!=(n<<3))
        fail(0x0B, n, n, local[0]);
    return r;
}
static void t11_stack(void) {
    debug_num(0x0B);
    u32 r = rec(200);
    if (r != 200u*201u/2u) fail(0x0B, 999, 200u*201u/2u, r);
}

/* ================= T12: soft mul/div (libgcc) vs shift-add reference ===== */
static u32 ref_mul(u32 a, u32 b) { u32 r=0; while (b) { if (b&1u) r+=a; a<<=1; b>>=1; } return r; }
static void t12_muldiv(void) {
    debug_num(0x0C);
    for (u32 i = 0; i < 300; i++) {
        u32 a = rnd(), b = rnd();
        u32 p = a * b;                              /* __mulsi3 */
        u32 rp = ref_mul(a, b);
        if (p != rp) fail(0x0C, i, rp, p);
        u32 d = (rnd() & 0xFFFFu) + 1u;
        u32 q = a / d, r = a % d;                   /* __udivsi3 / __umodsi3 */
        if (r >= d)                 fail(0x0C, 0x1000+i, d, r);
        if (ref_mul(q, d) + r != a) fail(0x0C, 0x2000+i, a, ref_mul(q,d)+r);
    }
    volatile s32 sn = -100, sd = 7;
    if (sn / sd != -14) fail(0x0C, 0x3000, (u32)-14, (u32)(sn/sd));
    if (sn % sd != -2)  fail(0x0C, 0x3001, (u32)-2,  (u32)(sn%sd));
}

/* ================= T13: snprintf-alike — format & parse back ============= */
/* Same core operations snprintf uses (udiv by 10, byte stores into a stack */
/* buffer), executed at 48 different stack depths so the buffer sweeps      */
/* across alignments and cache sets — the position-dependence catcher.      */
static u32 fmt_u_dec(char *buf, u32 v) {
    char t[12]; u32 i = 0;
    do { t[i++] = (char)('0' + (v % 10u)); v /= 10u; } while (v);
    u32 n = i; for (u32 j = 0; j < n; j++) buf[j] = t[n-1-j];
    buf[n] = 0; return n;
}
static u32 parse_u_dec(const char *s) { u32 v=0; while (*s) v = v*10u + (u32)(*s++ - '0'); return v; }
static void fmt_u_hex(char *buf, u32 v) {
    static const char hx[] = "0123456789ABCDEF";
    for (u32 i = 0; i < 8; i++) buf[i] = hx[(v >> (28 - 4*i)) & 0xFu];
    buf[8] = 0;
}
static u32 parse_u_hex(const char *s) {
    u32 v = 0;
    while (*s) { char c = *s++; v = (v<<4) | (u32)(c <= '9' ? c-'0' : c-'A'+10); }
    return v;
}
static void fmt_at_depth(u32 depth) {
    volatile u8 pad[4];
    pad[0] = (u8)depth; pad[1] = (u8)~depth; pad[2] = 0x5A; pad[3] = 0xA5;
    if (depth) { fmt_at_depth(depth - 1); }
    else {
        for (u32 i = 0; i < 64; i++) {
            char buf[16];
            u32 v = rnd();
            fmt_u_dec(buf, v);
            u32 back = parse_u_dec(buf);
            if (back != v) fail(0x0D, i, v, back);
            fmt_u_hex(buf, v);
            back = parse_u_hex(buf);
            if (back != v) fail(0x0D, 0x100+i, v, back);
        }
    }
    if (pad[0]!=(u8)depth || pad[1]!=(u8)~depth || pad[2]!=0x5A || pad[3]!=0xA5)
        fail(0x0D, 0xE00+depth, depth, pad[0]);
}
static void t13_format(void) {
    debug_num(0x0D);
    for (u32 d = 0; d < 48; d++) fmt_at_depth(d);
}

/* ================= T14: JALR / indirect calls ============================ */
typedef u32 (*fn2)(u32, u32);
static u32 f_add(u32 a, u32 b) { return a + b; }
static u32 f_xor(u32 a, u32 b) { return a ^ b; }
static u32 f_sub(u32 a, u32 b) { return a - b; }
static u32 f_or (u32 a, u32 b) { return a | b; }
static void t14_jalr(void) {
    debug_num(0x0E);
    volatile fn2 tab[4];
    tab[0]=f_add; tab[1]=f_xor; tab[2]=f_sub; tab[3]=f_or;
    for (u32 i = 0; i < 400; i++) {
        u32 a = rnd(), b = rnd(), k = rnd() & 3u;
        u32 g = tab[k](a, b);
        u32 e = (k==0) ? a+b : (k==1) ? (a^b) : (k==2) ? a-b : (a|b);
        if (g != e) fail(0x0E, i, e, g);
    }
}

/* ================= T15: icache miss + dirty dcache arbiter storm ========= */
/* Functions spread 256B apart force icache line misses through the arbiter */
/* while the dcache holds dirty lines and forces writebacks between calls.  */
static volatile u32 *g_fp;
#define FAR __attribute__((noinline, aligned(256)))
FAR static u32 far_a(u32 x){ g_fp[ 0]=x^0xA0A0u; return x + 0x111u; }
FAR static u32 far_b(u32 x){ g_fp[ 4]=x^0xB0B0u; return x ^ 0x222u; }
FAR static u32 far_c(u32 x){ g_fp[ 8]=x^0xC0C0u; return x + 0x333u; }
FAR static u32 far_d(u32 x){ g_fp[12]=x^0xD0D0u; return x ^ 0x444u; }
FAR static u32 far_e(u32 x){ g_fp[16]=x^0xE0E0u; return x + 0x555u; }
FAR static u32 far_f(u32 x){ g_fp[20]=x^0xF0F0u; return x ^ 0x666u; }
FAR static u32 far_g(u32 x){ g_fp[24]=x^0x1010u; return x + 0x777u; }
FAR static u32 far_h(u32 x){ g_fp[28]=x^0x2020u; return x ^ 0x888u; }
static void t15_arbiter_storm(void) {
    debug_num(0x0F);
    g_fp = (volatile u32*)0x00250000u;
    static const u8 slots[8] = {0,16,4,20,8,24,12,28};
    for (u32 i = 0; i < 600; i++) {
        u32 x = rnd();
        u32 r = x;
        r = far_a(r); r = far_e(r); r = far_b(r); r = far_f(r);
        r = far_c(r); r = far_g(r); r = far_d(r); r = far_h(r);
        u32 v = x, es[8];
        es[0]=v^0xA0A0u; v+=0x111u;
        es[1]=v^0xE0E0u; v+=0x555u;
        es[2]=v^0xB0B0u; v^=0x222u;
        es[3]=v^0xF0F0u; v^=0x666u;
        es[4]=v^0xC0C0u; v+=0x333u;
        es[5]=v^0x1010u; v+=0x777u;
        es[6]=v^0xD0D0u; v^=0x444u;
        es[7]=v^0x2020u; v^=0x888u;
        if (r != v) fail(0x0F, i, v, r);
        /* force writebacks of the dirty lines via 1KB aliases, then verify */
        volatile u32 *alias = g_fp + 1024u/4u;
        for (u32 k = 0; k < 8; k++) alias[k*4] = i ^ k;
        for (u32 k = 0; k < 8; k++)
            if (g_fp[slots[k]] != es[k]) fail(0x0F, 0x1000+(i<<3)+k, es[k], g_fp[slots[k]]);
    }
}

/* ========================================================================= */
int main(void) {
    g_errors = 0; g_pass = 0; rng_state = 1u;
    debug_log("cpu soak: start\n");
    paint(0x00u);

    while (1) {
        rng_state = 0xB5297A4Du ^ ref_mul(g_pass, 0x9E3779B9u) ^ 1u;
        t01_alu();            t02_shift();          t03_branch();
        t04_load_ext();       t05_subword_store();  t06_store_load_hazard();
        t07_pointer_chase();  t08_alias_writeback();t09_byte_storm();
        t10_copy_matrix();    t11_stack();          t12_muldiv();
        t13_format();         t14_jalr();           t15_arbiter_storm();
        g_pass++;
        debug_log("pass complete\n");
        debug_num(0xA000u | (g_pass & 0xFFFu));
        if (g_errors == 0) paint(GREEN);
        else               paint(RED);
    }
}