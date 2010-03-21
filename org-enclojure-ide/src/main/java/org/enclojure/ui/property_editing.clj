(comment
;*******************************************************************************
;*    Copyright (c) ThorTech, L.L.C.. All rights reserved.
;*    The use and distribution terms for this software are covered by the
;*    GNU General Public License, version 2
;*    (http://www.gnu.org/licenses/old-licenses/gpl-2.0.html) with classpath
;*    exception (http://www.gnu.org/software/classpath/license.html)
;*    which can be found in the file GPL-2.0+ClasspathException.txt at the root
;*    of this distribution.
;*    By using this software in any fashion, you are agreeing to be bound by
;*    the terms of this license.
;*    You must not remove this notice, or any other, from this software.
;*******************************************************************************
;*    Author: Eric Thorsen
;*******************************************************************************
)
(ns org.enclojure.ui.property-editing
  (:require
    [org.enclojure.commons.c-slf4j :as logger]
    )
  (:import (javax.swing Icon ImageIcon DefaultListModel)
    (java.util.logging Level)
    (java.beans PropertyEditor PropertyEditorManager)
    (javax.swing JTable JComboBox JFrame JScrollBar DefaultCellEditor JScrollPane)
    (javax.swing.table AbstractTableModel TableModel)
    ))

; setup logging
(logger/ensure-logger)

(defn editable-table
  "<columns> is a list of column definitions which can be just an atom used
the column name and they key or a sequence where (first s) is the name and
(fnext s) is the key.
<seq-of-maps> is a sequence of maps to edit."
  [columns seq-of-maps]
  (let [; convert the seq of maps into a mutable version for editing.
        data-list (reduce #(conj %1 (ref %2)) [] seq-of-maps)
        column-names ; build a map from column name to a key in the maps.
            (reduce (fn [v c]
                      (conj v
                        (if (or (map? c) (vector? c) (list? c))
                            {:name (first c) :key (fnext c)}
                          {:name c :key c}))) [] columns)]
    (proxy [AbstractTableModel] []
      (getRowCount [] (count data-list))
      (getColumnCount [] (count column-names))
      (getValueAt [r c]
        (@(data-list r) (:key (column-names c))))
      (getColumnName [i]
        (:name (column-names i)))
      (getColumnClass [c]
        (class (.getValueAt this 0 c)))
      (isCellEditable [r c] true)
      (setValueAt [value r c]
        (let [m (data-list r)]
            (when m
                (dosync
                    (alter m assoc (:key (column-names c)) value))))))))
        
(defn test-editor
  []
  (let [frame (JFrame.)
        model (editable-table [["Name" :name] ["Value" :value]]
                (repeat 5 {:name "this" :value 1}))
        jtable (JTable. model)]
    (.createDefaultColumnsFromModel jtable)
    ;(.setDefaultCloseOperation frame JFrame/EXIT_ON_CLOSE)
    (.add frame (JScrollPane. jtable))
    (.setSize frame 300 300)
    (.setVisible frame true)))
    




