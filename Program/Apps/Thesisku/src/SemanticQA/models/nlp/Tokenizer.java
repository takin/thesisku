/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */

package SemanticQA.models.nlp;

import SemanticQA.helpers.Constant;
import SemanticQA.helpers.SQLConnector;
import SemanticQA.listeners.TokenizerListener;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.List;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * POSTagger merupakan bagian dari pre-process NLP
 fungsi kelas ini adalah untuk membentuk token-token dari kata yang diinputkan
 
 Token yang telah dibentuk selanjutnya akan di cek kelas katanya ke dalam 
 database SQL untuk selanjutnya digunakan untuk memberikan TAG (POS TAG)
 sehingga nantinya token tersebut dapat di proses lebih lanjut oleh parser
 
 Apabila sebuah token tidak diketahui kelas katanya, maka kata/token tersebut 
 akan diberikan tag UN (unknwon)
 * @author syamsul
 */
public class Tokenizer extends SQLConnector {
    
    /**
     * Arraylist kalimat yang sudah dibentuk menjadi token per-kata.
     * 
     * Setelah proses TAGGING, apabila token pada array list ini ditemukan 
     * kelas katanya di dalam database, maka akan dimodifikasi yaitu dengan 
     * menambahkan tipe di akhir kata dengan dipisahkan oleh tanda ";"
     * 
     * misalnya token SIAPA, setelah proses TAGGing akan dimodifikasi menjadi 
     * SIAPA;PRON
     */
    private static List<String> TOKEN;
    
    // listener untuk dikirimkan ke kelas Observer (dalam hal ini kelas Process)
    private static TokenizerListener tokenizerListener;
    
    // string kalimat yang akan di tagging
    private static String SENTENCE;
    
    public Tokenizer(String sentence) throws ClassNotFoundException, InstantiationException, IllegalAccessException, SQLException {
        // lakukan proses inisialisasi koneksi dengan database
        super();
        TOKEN = new ArrayList<>();
        SENTENCE = sentence;
    }
    
    public static Tokenizer tokenize(String sentence) {
        Tokenizer tokenizer = null;
        try {
            tokenizer = new Tokenizer(sentence);
        } catch (ClassNotFoundException | InstantiationException | IllegalAccessException | SQLException ex) {
            Logger.getLogger(Tokenizer.class.getName()).log(Level.SEVERE, null, ex);
        }
        
        return tokenizer;
    }
    
    public static void then(TokenizerListener listener){
        tokenizerListener = listener;
        process();
    }
    
    
    private static void process(){
        
        try{
            /**
             * lakukan tokenisasi terhadap kalimat yang diinputkan
             * token ini nantinya akan digunakan sebagai clausa dalam SQL 
             * untuk mencari tipe kata masing-masing token
             * 
             * buat token dengan menggunakan pemisah spasi
             */
            String[] token = SENTENCE.split(" ");
            
            /**
             * buat query sql untuk mencari tipe kata di dalam database
             * clause IN dipilih dengan alasan agar proes query ke dalam database 
             * tidak dilakukan berulang-ulang, sehingga dapat meningkatkan 
             * performa, terutama pada query dengan jumlah token yang banyak
             */
            String SQL_QUERY = "SELECT katadasar,kode_katadasar FROM tb_katadasar WHERE katadasar IN (";
            
            /**
             * lakukan iterasi untuk memasukkan masing-masing kata yang akan
             * dijadikan sebagai kriteria di dalam kalusa IN
             */
            for(int i=0; i < token.length; i++){
                
                /**
                 * Siapkan kata yang akan dicek
                 * kata dalam database menggunakan lowercase, untuk itu pastikan 
                 * kata yang akan dimasukkan juga dalam format lowe case
                 * serta jangan lupa pula untuk melakukan trimming untuk membuang
                 * tanda baca lain seperti koma dan titik yang menjadi satu 
                 * dengan kata
                 */
                String word = token[i].toLowerCase().trim();
                
                TOKEN.add(word);
               
                /**
                 * Lakukan concatinate terhadap SQL_QUERY sehingga membentuk
                 * array string yang akan di cek di dalam database
                 * 
                 * Oleh karen kriteria yang akan dicek berupa string, 
                 * maka jangan lupa untuk menambahkan tanda petik (')
                 * pada setiap iterasi
                 * 
                 * Cek juga apakah posisi pointer sudah berada di akhir array 
                 * atau belum, jika belum maka tambahkan tanda koma pada setiap 
                 * akhir kata yang akan digabungkan
                 */
                SQL_QUERY += (i == (token.length - 1)) ? "'" + word + "'" : "'" + word + "',";
            }
            
            /**
             * setelah semua string kriteria dimasukkan, tambahkan tanda 
             * kurung tutup pada akhir query sehingga membentuk statement 
             * SQL yang utuk: Select katadasar, kode_katadasar from 
             * tb_katadasar where katadasar in ('a','b',c')
             */
            SQL_QUERY += ")";
            
            // buat statement SQL
            Statement stmt = SQLConnector.CONNECTION.createStatement();
            
            // lakukan query ke database
            ResultSet queryResult = stmt.executeQuery(SQL_QUERY);
            
           // cek apakah query menghasilkan result atau tidak
           if(queryResult.isBeforeFirst()){
               
               while(queryResult.next()){
                   
                   String kata = queryResult.getString("katadasar");
                   String kode = queryResult.getString("kode_katadasar");
                   
                   /**
                    * Untuk masing-masing kata yang ditemukan kelas katanya
                    * lakukan proses modifikasi yaitu dengan menambahkan kelasnya 
                    * di akhir dengan dippisahkan oleh tanda ';'
                    */
                   
                   // ambil index array dari kata yang bersangkutan
                   int indexOfTheToken = TOKEN.indexOf(kata);
                   
                   // modifikasi isi array
                   TOKEN.set(indexOfTheToken, kata + ";" + kode);
               }
           }
           
           queryResult.close();
           stmt.close();
           SQLConnector.CONNECTION.close();
           
           // broadcast hasil tag
           tokenizerListener.onTokenizeSuccess(TOKEN);
            
        } catch( SQLException e ){
            tokenizerListener.onTokenizeFail(e.getMessage());
        }
    }
}