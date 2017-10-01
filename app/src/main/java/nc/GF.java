package nc;

/**
 * 有限域上的工具包
 * gf_init,gf_uninit
 * gf_add,gf_sub,gf_mul,gf_div,gf_exp
 * @author Administrator
 *
 */
public class GF {
	private int gFieldSize;
	private int[] table_alpha;
	private int[] table_index;

	private byte[][] table_mul;
	private byte[][] table_div;
	private static int[] prim_poly = { 0x00000000, 0x00000001, 0x00000007, 0x0000000b, 0x00000013, 0x00000025,
			0x00000043, 0x00000089, 0x00000187, 0x00000211, 0x00000409, 0x00000805, 0x00001053 };

	// 初始化有限域
	public void init(int m, int prim)// GF(2^m), primitive polymonial
	{
		int i = 0, j = 0;

		if (m > 12) // the field size is supported from GF(2^1) to GF(2^12).
			return;

		gFieldSize = 1 << m;

		if (0 == prim)
			prim = prim_poly[m];

		table_alpha = new int[gFieldSize];
		table_index = new int[gFieldSize];
		table_mul = new byte[gFieldSize][gFieldSize];
		table_div = new byte[gFieldSize][gFieldSize];

		table_alpha[0] = 1;
		table_index[0] = -1;

		for (i = 1; i < gFieldSize; i++) {
			table_alpha[i] = table_alpha[i - 1] << 1;
			if (table_alpha[i] >= gFieldSize) {
				table_alpha[i] ^= prim;
			}

			table_index[table_alpha[i]] = i;
		}

		table_index[1] = 0;

		// create the tables of mule and div
		for (i = 0; i < gFieldSize; i++)
			for (j = 0; j < gFieldSize; j++) {
				table_mul[i][j] = (byte) gfmul(i, j);
				table_div[i][j] = (byte) gfdiv(i, j);
			}

	}

	public void uninit() {
		// 释放空间 gcc负责回收
		table_alpha = null;
		table_index = null;
		table_mul = null;
		table_div = null;
	}

	// 加
	public int add(int a, int b) {
		return a ^ b;
	}

	// 减
	public int sub(int a, int b) {
		return a ^ b;
	}

	// 乘
	public int mul(int a, int b) {
		// 从表中查结果
		int result = table_mul[a][b];
		if (result < 0) {
			result = 256 - Math.abs(result);
		}
		return result;
	}

	// 除
	public int div(int a, int b) {
		int result = table_div[a][b];
		if (result < 0) {
			result = 256 - Math.abs(result);
		}
		return result;
	}

	// 乘方
	public int exp(int a, int n) {
		if (a == 0 && n == 0) {
			return 1;
		}
		if (a == 0 && n != 0) {
			return 0;
		}
		return table_alpha[table_index[a] * n % (gFieldSize - 1)];
	}

	private int gfmul(int a, int b) {
		if (0 == a || 0 == b)
			return 0;

		return table_alpha[(table_index[a] + table_index[b]) % (gFieldSize - 1)];
	}

	private int gfdiv(int a, int b) {
		if (0 == a || 0 == b)
			return 0;

		return table_alpha[(table_index[a] - table_index[b] + (gFieldSize - 1)) % (gFieldSize - 1)];
	}

}
