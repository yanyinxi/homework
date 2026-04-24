package com.homework.asset.ingest.excel;

import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.apache.poi.hssf.usermodel.HSSFWorkbook;
import org.apache.poi.ss.usermodel.Cell;
import org.apache.poi.ss.usermodel.CellType;
import org.apache.poi.ss.usermodel.FormulaEvaluator;
import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.ss.usermodel.Workbook;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;

/** Apache POI 封装：将 .xls/.xlsx 的第一个 Sheet 读取为 Map 列表，首行作为 Header。 */
public final class ExcelReader {

  private ExcelReader() {}

  /**
   * 读取 Excel 文件，返回每行数据的 Map（key = 列头，value = 原始值对象）。
   *
   * @param stream 文件输入流
   * @param fileName 文件名（用于判断 xls 或 xlsx）
   * @return 数据行列表，每行是 header→value 的有序 Map
   */
  public static List<Map<String, Object>> read(InputStream stream, String fileName)
      throws IOException {
    try (Workbook wb = openWorkbook(stream, fileName)) {
      Sheet sheet = wb.getSheetAt(0);
      List<String> headers = readHeaders(sheet);
      FormulaEvaluator evaluator = wb.getCreationHelper().createFormulaEvaluator();
      return readRows(sheet, headers, evaluator);
    }
  }

  private static Workbook openWorkbook(InputStream stream, String fileName) throws IOException {
    if (fileName != null && fileName.toLowerCase().endsWith(".xlsx")) {
      return new XSSFWorkbook(stream);
    }
    // 默认按 .xls（HSSF）处理
    return new HSSFWorkbook(stream);
  }

  /** 读取第 0 行作为列头。 */
  private static List<String> readHeaders(Sheet sheet) {
    Row headerRow = sheet.getRow(0);
    List<String> headers = new ArrayList<>();
    if (headerRow == null) return headers;
    for (Cell cell : headerRow) {
      headers.add(cellToString(cell));
    }
    return headers;
  }

  /** 从第 1 行开始读取数据行。 */
  private static List<Map<String, Object>> readRows(
      Sheet sheet, List<String> headers, FormulaEvaluator evaluator) {
    List<Map<String, Object>> rows = new ArrayList<>();
    for (int i = 1; i <= sheet.getLastRowNum(); i++) {
      Row row = sheet.getRow(i);
      if (row == null || isBlankRow(row)) continue;
      Map<String, Object> rowMap = new LinkedHashMap<>();
      for (int j = 0; j < headers.size(); j++) {
        Cell cell = row.getCell(j);
        rowMap.put(headers.get(j), cellToValue(cell, evaluator));
      }
      rows.add(rowMap);
    }
    return rows;
  }

  /** 将 Cell 转换为合适的 Java 类型（数值、字符串、布尔）。 */
  private static Object cellToValue(Cell cell, FormulaEvaluator evaluator) {
    if (cell == null) return null;
    CellType type = cell.getCellType();
    if (type == CellType.FORMULA) {
      type = evaluator.evaluate(cell).getCellType();
    }
    return switch (type) {
      case NUMERIC -> cell.getNumericCellValue();
      case STRING -> {
        String s = cell.getStringCellValue().strip();
        yield s.isEmpty() ? null : s;
      }
      case BOOLEAN -> cell.getBooleanCellValue();
      case BLANK, _NONE -> null;
      default -> cell.toString();
    };
  }

  private static String cellToString(Cell cell) {
    if (cell == null) return "";
    return cell.toString().strip();
  }

  private static boolean isBlankRow(Row row) {
    for (Cell cell : row) {
      if (cell != null && cell.getCellType() != CellType.BLANK) return false;
    }
    return true;
  }
}
